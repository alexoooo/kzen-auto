package tech.kzen.auto.server.objects.job.worker.format

import tech.kzen.auto.common.data.format.ConfiguredRecordFormat
import tech.kzen.auto.common.objects.document.job.path.WriterColumnSpec
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.data.write.RecordWriterCapability
import tech.kzen.auto.server.data.write.RecordWriterCapabilityRegistry
import tech.kzen.auto.server.objects.job.value.recycle.RecyclablePool
import tech.kzen.auto.server.objects.job.worker.Emitter
import tech.kzen.auto.server.objects.job.worker.JobLaneAttempt
import tech.kzen.auto.server.objects.job.worker.JobLaneContext
import tech.kzen.auto.server.objects.job.worker.JobLaneDescriptor
import tech.kzen.auto.server.objects.job.worker.TransformWorker
import tech.kzen.auto.server.objects.job.worker.WriterColumns
import tech.kzen.auto.server.objects.job.worker.content.Bytes
import tech.kzen.auto.server.objects.job.worker.content.PooledBytes
import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.util.digest.Digest


/**
 * Encodes each incoming record in [format], emitting it as one chunk of [Bytes] with metadata `{name, parent}`:
 * `name` is [name] resolved for the record ([FormatName]), and `parent` is the record's own metadata. Consecutive
 * records with the same name are one output, so the chunk that starts each output begins with the format's header;
 * no records, no chunks. [columns] chooses the cells as Export's do ([WriterColumns]); the header names them.
 *
 * The encoder is made from the input lane's contract, so what the format cannot write (a format with no writer,
 * columns that differ from its schema, a character set that cannot encode its delimiter) is refused before Run, as
 * a validation error on the Worker; a record it cannot write fails the run, naming the record. Only an untyped lane
 * waits for its first record.
 *
 * The chunks are pooled, and their pool belongs to this instance: a consumer copies a chunk inside its callback.
 *
 * A live edit carries the output left open into the edited instance. While the bytes it writes stay the same
 * (format, columns, and the header the input contract gives), that output continues with no second header; an
 * edited name only names the records that follow, so a name that changes ends the output as any change of name
 * does. Otherwise the open output is given up: the next chunk starts it again with the new header and is marked a
 * restart ([Bytes]), so `Write` discards what it holds of it.
 */
@Reflect
class FormatWorker(
    input: ChannelInput<*>,
    output: ChannelOutput<DataValue>,
    private val format: ConfiguredRecordFormat,
    name: String,
    private val columns: WriterColumnSpec,
    selfLocation: ObjectLocation,
    @Service private val writers: RecordWriterCapabilityRegistry
):
    TransformWorker(input, output, selfLocation)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val defaultName = "output\${${FormatName.extensionPlaceholder}}"
    }


    /** What a live edit carries: the output left open (by name, with the encoding of its bytes), and the count. */
    private class Carried(
        val encoding: Digest?,
        val openName: String?,
        val received: Long
    )


    //-----------------------------------------------------------------------------------------------------------------
    private val template = name.ifBlank { defaultName }
    private val extension = format.extensions.firstOrNull()?.let { ".$it" } ?: ""
    private val writerColumns = WriterColumns(columns)

    // Acquired only on this Worker's coroutine
    private val pool = RecyclablePool { PooledBytes(it) }

    private var chunker: FormatChunker? = null
    private var received = 0L
    private var carried: Carried? = null


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onStart(control: JobControl) {
        writer()
        val contract = control.inputContract()
            ?.takeUnless { it.structural is DataType.Dynamic }
            ?: return
        chunker = adopt(chunker(contract))
    }


    override suspend fun onElement(element: DataValue, emit: Emitter, control: JobControl) {
        val active = chunker
            ?: adopt(chunker(element.contract)).also { chunker = it }
        received += 1
        val chunk = active.chunk(element, received)
        carried = null
        emit.send(chunk)
    }


    /** Chunks of [Bytes] named for their records; known before Run, so the format is checked there. */
    override fun payloadFlow(input: JobLaneDescriptor, context: JobLaneContext): JobLaneAttempt {
        val dynamic = input.contract.structural is DataType.Dynamic
        val parent =
            if (dynamic) {
                DataContract(DataType.Dynamic())
            }
            else {
                input.contract.metadata?.contract
            }
        val error =
            try {
                if (dynamic) {
                    writer()
                }
                else {
                    chunker(input.contract)
                }
                null
            }
            catch (e: IllegalArgumentException) {
                e.message
            }
        return JobLaneAttempt(JobLaneDescriptor(FormatChunker.contract(parent)), error)
    }


    // Until this instance has chunked a record, the carried state still describes the open output
    override fun captureMigrationState(): Any =
        carried ?: Carried(chunker?.encoding, chunker?.openName(), received)


    override fun loadMigrationState(captured: Any?) {
        val state = captured as? Carried
            ?: return
        received = state.received
        carried = state
    }


    /** Continues the carried open output in [made] when it encodes the same bytes, or else restarts it. */
    private fun adopt(made: FormatChunker): FormatChunker {
        val state = carried
            ?: return made
        val openName = state.openName
            ?: return made
        if (state.encoding == made.encoding) {
            made.resume(openName)
        }
        else {
            made.restart()
        }
        return made
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun writer(): RecordWriterCapability =
        writers.writerFor(format)
            ?: throw IllegalArgumentException("${format.title} cannot be written, only read")


    /** Fails by name when the format cannot write records of [contract]. */
    private fun chunker(contract: DataContract): FormatChunker {
        val writer = writer()
        writerColumns.staticError(contract)?.let { throw IllegalArgumentException(it) }
        val header =
            try {
                writerColumns.header(contract) { it.render() }
            }
            catch (e: DataException) {
                throw IllegalArgumentException("Columns: ${e.message}", e)
            }
        val encoder = writer.encoder(format, header)
        val cells = FormatCells(writerColumns, contract)
        val encoding = Digest.build {
            addDigestible(format)
            addDigestible(columns)
            addInt(header.size)
            header.forEach(::addUtf8)
        }
        return FormatChunker(encoder, cells, FormatName(template, extension, cells, contract), pool, encoding)
    }
}
