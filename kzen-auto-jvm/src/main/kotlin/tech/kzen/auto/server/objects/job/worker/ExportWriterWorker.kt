package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.objects.document.report.spec.output.OutputExportSpec
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.common.util.data.DataLocationGroup
import tech.kzen.auto.plugin.model.data.DataRecordBuffer
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.objects.report.exec.output.export.format.CsvExportFormatter
import tech.kzen.auto.server.objects.report.exec.output.export.format.RecordFormat
import tech.kzen.auto.server.objects.report.exec.output.export.format.TsvExportFormatter
import tech.kzen.auto.server.objects.report.exec.output.export.model.ExportCompression
import tech.kzen.auto.server.objects.report.exec.output.export.model.ExportFormat
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import java.io.Closeable
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.math.ceil
import kotlin.time.Clock


/**
 * The EXPORT stage as a Job Worker — writes the incoming stream to a delimited file using Report's exact export
 * encoding, so a Job export is byte-identical to Report's over the same data (the P4j A/B gate). It reuses
 * Report's substrate-neutral leaf engines (NOT its disruptor pipeline): the [RecordFormat]
 * ([CsvExportFormatter] / [TsvExportFormatter]) for RFC-4180 CSV / TSV formatting, an in-place UTF-8 encode
 * mirroring [tech.kzen.auto.server.objects.report.exec.output.export.CharsetExportEncoder], and the shared
 * [ExportCompression.wrap] none/zip/gz seam (extracted from `CompressedExportWriter` so both call it). The
 * column header is written once (from the first record's header, via `render` — matching Report's
 * `ExportFormatter`), then each record follows.
 *
 * `export` is an [OutputExportSpec]: `format` (csv / tsv), `compression` (none / zip / gz), and `path` — a
 * pattern resolved with `${time}` = now, `${extension}` = the format+compression extension, `${report}` = this
 * Worker's own document name, `${group}` = empty (a Job export is a single stream → a single file, unlike
 * Report's per-group files). Report's `OutputExportSpec.resolvePath` is reused, so extensions / placeholders
 * behave identically.
 *
 * A [SinkWorker] (no output channel, no serve): the framework owns the drain loop, per-batch checkpoint, and
 * throttled written-row progress. The file is opened in [onStart] and the compression container finalized +
 * closed in [onClose] (completion, failure, cancel alike), the writes running through [JobControl.runBlockingIo]
 * so the IO stays counted by quiescence detection. If the stream is empty (every record filtered upstream) the
 * file is an empty container — the header is only known from a record — matching [CsvWriterWorker].
 *
 * LIVE-EDIT MIGRATION: like [CsvWriterWorker] and every file sink, this uses the [WorkerBase] RESTART default —
 * a rebuilt instance re-runs [onStart], re-resolving the path and RE-TRUNCATING (a compressed stream cannot be
 * appended-to mid-file). This is the coherent sink default; a resumable / re-run-from-scratch export across a
 * live edit is a documented follow-up (in practice an export is run to completion, then re-run after an edit).
 */
@Reflect
class ExportWriterWorker(
    input: ChannelInput<Any?>,

    private val export: OutputExportSpec,
    private val selfLocation: ObjectLocation
):
    SinkWorker(input, selfLocation)
{
    //-----------------------------------------------------------------------------------------------------------------
    private val recordFormat: RecordFormat = when (ExportFormat.byName(export.format)) {
        ExportFormat.Csv -> CsvExportFormatter()
        ExportFormat.Tsv -> TsvExportFormatter()
    }

    // Reused across rows (format writes chars → encode writes bytes, both in place), like Report's export pipeline.
    private val encoder = StandardCharsets.UTF_8.newEncoder()
    private val maxBytesPerChar = encoder.maxBytesPerChar().toDouble()
    private val buffer = DataRecordBuffer()

    private var out: OutputStream? = null
    private var closer: Closeable? = null
    private var headerWritten = false
    private var written = 0L


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onStart(control: JobControl) {
        val time = Clock.System.now()
        val documentName = selfLocation.documentPath.name
        val resolvedPath = export.resolvePath(documentName, DataLocationGroup.empty, time)
        val innerFilename = export.resolveInnerFilename(documentName, DataLocationGroup.empty, time)
        val file = toFilePath(resolvedPath).toAbsolutePath().normalize()

        control.runBlockingIo {
            Files.createDirectories(file.parent)
            val wrapped = ExportCompression.wrap(Files.newOutputStream(file), export, innerFilename)
            out = wrapped.out
            closer = wrapped.closer
        }
    }


    override suspend fun onElement(element: JobMessage, control: JobControl) {
        val out = out!!
        val flat = element.flatView()
        val elementHeader = flat.header
        val record = flat.record
        control.runBlockingIo {
            if (! headerWritten) {
                // The column header, once — rendered exactly as Report's ExportFormatter (`render` disambiguates
                // duplicate-occurrence columns, e.g. "amount (2)").
                writeRow(out, FlatFileRecord.of(elementHeader.values.map { it.render() }))
                headerWritten = true
            }
            writeRow(out, record)
            written += 1
        }
    }


    override fun onClose() {
        // Finalizes the compression container (a Zip closes its entry first) and closes the underlying file.
        closer?.close()
    }


    override fun progress(snapshot: Any?): Map<String, Any?> =
        mapOf("written" to written)


    //-----------------------------------------------------------------------------------------------------------------
    private fun writeRow(out: OutputStream, record: FlatFileRecord) {
        buffer.clear()
        recordFormat.format(record, buffer)
        encode(buffer)
        out.write(buffer.bytes, 0, buffer.bytesLength)
    }


    // Mirrors CharsetExportEncoder: encode the buffer's formatted chars to bytes in place (UTF-8).
    private fun encode(output: DataRecordBuffer) {
        val charsLength = output.charsLength
        val maxOutputLength = ceil(maxBytesPerChar * charsLength).toInt()
        output.ensureByteCapacity(maxOutputLength)

        val inputBuffer = output.initializedCharBuffer(charsLength)
        val outputBuffer = output.initializedByteBuffer(maxOutputLength)

        encoder.reset()
        encoder.encode(inputBuffer, outputBuffer, true)
        encoder.flush(outputBuffer)

        output.bytesLength = outputBuffer.position()
    }
}
