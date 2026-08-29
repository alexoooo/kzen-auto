package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.auto.common.util.PathPatternSubstitution
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.data.FileListingAction
import tech.kzen.auto.server.service.compile.CachedKotlinCompiler
import tech.kzen.auto.server.util.ClassLoaderUtils
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import java.io.BufferedWriter
import java.nio.file.Files
import java.nio.file.Path


/**
 * The CSV output stage as a Job Worker (analogue of `CompressedExportWriter`, minus compression). Writes each
 * value's column projection (a scalar value projects to a synthetic `value` column, so a scalar stream
 * writes a `value` column). When [header] is true the column names are written once (from the first batch)
 * before the records; when false (a headerless round-trip) only records are written. Fields are written with RFC-4180 quoting that is
 * DELIMITER-AWARE: a field is quoted when it contains the [delimiter], a quote, or a line break, and quotes
 * are escaped by doubling (the same rules as `FlatFileRecord.writeCsvField`, but parameterized on the
 * configured delimiter rather than hard-coding the comma — so a `;`-delimited round-trip is correct).
 *
 * A [SinkWorker]: the framework owns the drain loop, per-batch checkpoint, and throttled written-row progress.
 * The file is opened in [onStart]. Successful completion closes it through counted blocking IO before stat and
 * yield; the final lifecycle cleanup uses the same idempotent finalizer and retries ownership retained after a
 * cancellation race or failed close. Failure/cancel therefore closes without yielding.
 *
 * If every record was filtered out upstream no batch arrives, [onStart] still creates an empty file. A nonblank
 * [result] declares this Worker as a ResultYielder: successful completion closes first, stats the final bytes,
 * then yields one fingerprinted plain [tech.kzen.auto.common.data.model.DataRef]; failure/cancel only closes.
 * Path placeholders read scalar Job parameters and use the shared literal substitution grammar.
 */
@Reflect
class CsvWriterWorker(
    input: ChannelInput<*>,

    private val path: String,
    private val delimiter: String,
    private val header: Boolean,
    private val result: String,

    private val selfLocation: ObjectLocation,
    @Service private val fileListingAction: FileListingAction,
    @Service private val cachedKotlinCompiler: CachedKotlinCompiler
):
    SinkWorker(input, selfLocation)
{
    private val delimiterChar: Char =
        if (delimiter.isEmpty()) ',' else delimiter[0]

    private val writer = RetriableCloseable<BufferedWriter>()
    private var outputPath: Path? = null
    private var headerWritten = false
    private var written = 0L


    override suspend fun onStart(control: JobControl) {
        WriterResultValidation.requireRuntime(
            result, control, cachedKotlinCompiler, ClassLoaderUtils.dynamicParentClassLoader())
        val values = PathPatternSubstitution.referencedNames(path).associateWith {
            WriterFilePath.parameterText(it, control.parameter(it))
        }
        val resolved = WriterFilePath.resolve(PathPatternSubstitution.substitute(path, values))
        outputPath = resolved
        control.runBlockingIo {
            WriterFilePath.prepare(resolved)
            writer.attach(Files.newBufferedWriter(resolved))
        }
    }


    override suspend fun onElement(element: DataValue, control: JobControl) {
        val writer = writer.requireOwned()
        val projection = JobDataValues.projection(element)
        val elementHeader = projection.header
        control.runBlockingIo {
            if (header && !headerWritten) {
                writeRecord(writer, FlatFileRecord.of(elementHeader.values.map { it.text }))
                headerWritten = true
            }
            writeProjection(writer, projection)
            written += 1
        }
    }


    override suspend fun onComplete(control: JobControl) {
        finalizeOutput(control)
        if (result.isNotBlank()) {
            val ref = WriterFilePath.finalizedRef(requireNotNull(outputPath), control, fileListingAction)
            val definition = control.results()[tech.kzen.lib.common.exec.data.binding.BindingName(result)]
            control.yieldResult(result, JobDataValues.lift(ref, definition.contract))
        }
    }


    override suspend fun onClose() {
        finalizeOutput(null)
    }


    override fun progress(snapshot: Any?): Map<String, Any?> =
        mapOf("written" to written)


    private suspend fun finalizeOutput(control: JobControl?) {
        writer.close(control)
    }


    override fun payloadFlow(input: JobLaneDescriptor, context: JobLaneContext): JobLaneAttempt {
        return JobLaneAttempt(
            input,
            WriterResultValidation.staticError(
                result, selfLocation, context, cachedKotlinCompiler))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun writeRecord(writer: BufferedWriter, record: FlatFileRecord) {
        for (fieldIndex in 0 until record.fieldCount()) {
            if (fieldIndex > 0) {
                writer.write(delimiter)
            }
            writeField(writer, record.getString(fieldIndex))
        }
        writer.newLine()
    }


    private fun writeProjection(
        writer: BufferedWriter,
        projection: tech.kzen.auto.server.objects.job.value.ColumnProjection
    ) {
        for (fieldIndex in 0 until projection.size) {
            if (fieldIndex > 0) {
                writer.write(delimiter)
            }
            writeField(writer, projection.render(fieldIndex))
        }
        writer.newLine()
    }


    private fun writeField(writer: BufferedWriter, value: String) {
        var needsQuote = false
        for (character in value) {
            if (character == delimiterChar || character == '"' || character == '\r' || character == '\n') {
                needsQuote = true
                break
            }
        }

        if (!needsQuote) {
            writer.write(value)
            return
        }

        writer.write("\"")
        for (character in value) {
            if (character == '"') {
                writer.write("\"")
            }
            writer.write(character.code)
        }
        writer.write("\"")
    }
}
