package tech.kzen.auto.server.objects.job.worker.format

import tech.kzen.auto.common.data.format.ConfiguredRecordFormat
import tech.kzen.auto.common.objects.document.job.JobFieldConventions
import tech.kzen.auto.common.objects.document.job.JobOutputConventions
import tech.kzen.auto.common.objects.document.job.path.WriterColumnSpec
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.data.write.RecordEncoder
import tech.kzen.auto.server.data.write.RecordWriterCapability
import tech.kzen.auto.server.data.write.RecordWriterCapabilityRegistry
import tech.kzen.auto.server.objects.job.expression.JobExpressionCompiler
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.objects.job.value.recycle.RecyclablePool
import tech.kzen.auto.server.objects.job.worker.Emitter
import tech.kzen.auto.server.objects.job.worker.JobLaneAttempt
import tech.kzen.auto.server.objects.job.worker.JobLaneContext
import tech.kzen.auto.server.objects.job.worker.JobLaneDescriptor
import tech.kzen.auto.server.objects.job.worker.TransformWorker
import tech.kzen.auto.server.objects.job.worker.WriterColumns
import tech.kzen.auto.server.objects.job.worker.content.Bytes
import tech.kzen.auto.server.objects.job.worker.content.PooledBytes
import tech.kzen.auto.server.util.ClassLoaderUtils
import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.util.digest.Digest


/**
 * Encodes each incoming record in [format], emitting it as one chunk of [Bytes] with metadata
 * `{group, format, parent}` ([ChunkMetadata]): `group` is [groupBy]'s value for the record (a Kotlin expression with
 * Filter's scope, its value as text), `""` for every record when [groupBy] is blank; `format` holds the format's
 * extension; `parent` is the record's own metadata. Each group is one output: its first chunk begins with the
 * format's header, and a last chunk holds its footer, marked as its end ([Bytes.endsOutput]). When outputs end is
 * [groupEnd]: `end` (the default) keeps every output open to the end of input, so a group's records may arrive
 * anywhere; `change` ends an output as soon as a record of another group arrives, so a group that comes back fails
 * the run. No records, no chunks. [columns] chooses the cells as Export's do ([WriterColumns]); the header names
 * them.
 *
 * The encoder and [groupBy] are made from the input lane's contract, so what the format cannot write (a format with
 * no writer, columns that differ from its schema, a character set that cannot encode its delimiter) and a [groupBy]
 * that does not compile are refused before Run, as a validation error on the Worker; a record it cannot write fails
 * the run, naming the record. Only an untyped lane waits for its first record (its [groupBy] is only parsed before).
 *
 * The chunks are pooled, and their pool belongs to this instance: a consumer copies a chunk inside its callback.
 *
 * A live edit carries the open outputs into the edited instance. While what it writes stays the same (format,
 * columns, the header the input contract gives, [groupBy] and [groupEnd]), every open output continues with no
 * second header. Otherwise each is given up: a chunk marked as its discard ([Bytes.discardsOutput]) tells `Write`
 * to drop what it holds of it, and a group that comes back starts its output again with the new header.
 */
@Reflect
class FormatWorker(
    input: ChannelInput<*>,
    output: ChannelOutput<DataValue>,
    private val format: ConfiguredRecordFormat,
    private val groupBy: String,
    groupEnd: String,
    private val columns: WriterColumnSpec,
    selfLocation: ObjectLocation,
    @Service private val writers: RecordWriterCapabilityRegistry,
    @Service private val jobExpressionCompiler: JobExpressionCompiler
):
    TransformWorker(input, output, selfLocation)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        const val groupEndAtEnd = "end"
        const val groupEndOnChange = "change"

        private const val groupByLabel = "Group by"
        private val groupByAttribute = AttributeName("groupBy")
    }


    /** How records of one contract are written: the [encoder], the [cells] it reads, and the [digest] of both. */
    private class Encoding(
        val encoder: RecordEncoder,
        val cells: FormatCells,
        val digest: Digest
    )


    /**
     * What a live edit carries: the outputs left open (with the encoding of their bytes, the [groupBy] that named
     * them, and the [contract] they were made for, which ends them when no record follows), and the counts.
     */
    private class Carried(
        val encoding: Digest?,
        val groupBy: String,
        val contract: DataContract?,
        val groups: FormatGroups?,
        val received: Long,
        val encodedBytes: Long
    )


    //-----------------------------------------------------------------------------------------------------------------
    private val endsOnChange = when (groupEnd.trim().lowercase()) {
        "", groupEndAtEnd -> false
        groupEndOnChange -> true
        else -> throw IllegalArgumentException(
            "Unsupported groupEnd '$groupEnd'; expected $groupEndAtEnd or $groupEndOnChange")
    }

    private val extension = format.extensions.firstOrNull() ?: ""
    private val writerColumns = WriterColumns(columns)
    private val classLoader = ClassLoaderUtils.dynamicParentClassLoader()

    // Acquired only on this Worker's coroutine
    private val pool = RecyclablePool { PooledBytes(it) }
    private val chunks = ArrayList<DataValue>()

    private var chunker: FormatChunker? = null
    private var chunkedContract: DataContract? = null
    private var received = 0L
    private var encodedBytes = 0L
    private var carried: Carried? = null


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onStart(control: JobControl) {
        writer()
        val contract = control.inputContract()
            ?.takeUnless { it.structural is DataType.Dynamic }
            ?: return
        chunker = chunker(contract, control)
    }


    override suspend fun onElement(element: DataValue, emit: Emitter, control: JobControl) {
        val active = chunker
            ?: chunker(element.contract, control).also { chunker = it }
        adoptCarried(active)
        received += 1
        active.chunk(element, received, chunks)
        send(emit)
    }


    override suspend fun onComplete(emit: Emitter, control: JobControl) {
        val active = chunker
            ?: carried?.contract?.let { chunker(it, control) }
            ?: return
        adoptCarried(active)
        active.complete(chunks)
        send(emit)
    }


    /** Chunks of [Bytes] of their records' groups; known before Run, so the format and [groupBy] are checked there. */
    override fun payloadFlow(input: JobLaneDescriptor, context: JobLaneContext): JobLaneAttempt {
        val dynamic = input.contract.structural is DataType.Dynamic
        val parent =
            if (dynamic) {
                DataContract(DataType.Dynamic())
            }
            else {
                input.contract.metadata?.contract
            }
        val lane = JobLaneDescriptor(FormatChunker.contract(parent))
        return try {
            if (dynamic) {
                writer()
                val syntaxError = groupBy.takeIf { it.isNotBlank() }?.let(jobExpressionCompiler::validateSyntax)
                JobLaneAttempt(lane, syntaxError?.let { "$groupByLabel: $it" })
            }
            else {
                encoding(input.contract)
                val attempt = groupBy.takeIf { it.isNotBlank() }?.let {
                    jobExpressionCompiler.compile(
                        "format_group", it, input.contract, input.payloadType ?: TypeMetadata.anyNullable,
                        context.classLoader, context.parameters)
                }
                // The type of Group by's value, shown in the field; its text is the group
                val type = attempt?.compiled?.contract?.let {
                    mapOf(JobFieldConventions.typeKey(groupByAttribute) to it.asExecutionValue().get())
                }
                JobLaneAttempt(lane, attempt?.error?.let { "$groupByLabel: $it" }, attempt?.warning)
                    .withDetails(type.orEmpty())
            }
        }
        catch (e: IllegalArgumentException) {
            JobLaneAttempt(lane, e.message)
        }
    }


    // Until this instance has chunked a record, the carried state still describes the open outputs
    override fun captureMigrationState(): Any =
        carried ?: Carried(chunker?.encoding, groupBy, chunkedContract, chunker?.groups, received, encodedBytes)


    override fun loadMigrationState(captured: Any?) {
        val state = captured as? Carried
            ?: return
        received = state.received
        encodedBytes = state.encodedBytes
        carried = state
    }


    override fun progress(snapshot: Any?): Map<String, Any?> {
        val progress = mutableMapOf<String, Any?>(
            JobOutputConventions.formatRecordsKey to received,
            JobOutputConventions.formatBytesKey to encodedBytes)
        if (groupBy.isNotBlank()) {
            val groups = carried?.groups ?: chunker?.groups
            progress[JobOutputConventions.formatGroupsOpenKey] = groups?.open?.size?.toLong() ?: 0L
            progress[JobOutputConventions.formatGroupsDoneKey] = groups?.done ?: 0L
        }
        return progress
    }


    //-----------------------------------------------------------------------------------------------------------------
    /** Continues the carried open outputs in [active] when it writes them the same way, or else restarts them. */
    private fun adoptCarried(active: FormatChunker) {
        val state = carried
            ?: return
        carried = null
        val groups = state.groups
            ?: return
        if (state.encoding == active.encoding) {
            active.resume(groups)
        }
        else {
            active.restart(groups, sameGroups = state.groupBy == groupBy)
        }
    }


    private suspend fun send(emit: Emitter) {
        for (chunk in chunks) {
            encodedBytes += (JobDataValues.native(chunk) as Bytes).length()
            emit.send(chunk)
        }
        chunks.clear()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun writer(): RecordWriterCapability =
        writers.writerFor(format)
            ?: throw IllegalArgumentException("${format.title} cannot be written, only read")


    /** Fails by name when the format cannot write records of [contract]. */
    private fun encoding(contract: DataContract): Encoding {
        val writer = writer()
        writerColumns.staticError(contract)?.let { throw IllegalArgumentException(it) }
        val header =
            try {
                writerColumns.header(contract) { it.render() }
            }
            catch (e: DataException) {
                throw IllegalArgumentException("Columns: ${e.message}", e)
            }
        val digest = Digest.build {
            addDigestible(format)
            addDigestible(columns)
            addInt(header.size)
            header.forEach(::addUtf8)
            addUtf8(groupBy)
            addBoolean(endsOnChange)
        }
        return Encoding(writer.encoder(format, header), FormatCells(writerColumns, contract), digest)
    }


    private suspend fun chunker(contract: DataContract, control: JobControl): FormatChunker {
        val encoding = encoding(contract)
        val groupOf = groupBy.takeIf { it.isNotBlank() }?.let { groupOf(contract, control) }
        chunkedContract = contract
        return FormatChunker(
            encoding.encoder, encoding.cells, groupOf, endsOnChange, extension, pool, encoding.digest)
    }


    /** [groupBy] compiled over records of [contract], as Filter compiles `where`: a record's group as text. */
    private suspend fun groupOf(contract: DataContract, control: JobControl): (DataValue) -> String {
        val parameters = control.parameters()
        val receiverType = control.payloadType() ?: TypeMetadata.anyNullable
        val expression = control.runBlockingIo {
            val attempt = jobExpressionCompiler.compile(
                "format_group", groupBy, contract, receiverType, classLoader, parameters)
            require(attempt.error == null) { "$groupByLabel: ${attempt.error}" }
            checkNotNull(attempt.compiled).expression
        }
        expression.setParameters(parameters.definitions.map { control.parameter(it.name.value) })
        val projected = contract.structural is DataType.Record || contract.structural is DataType.Scalar
        return { element ->
            val projection = if (projected) JobDataValues.projection(element) else null
            expression.evaluate(JobDataValues.native(element), element, projection).toString()
        }
    }
}
