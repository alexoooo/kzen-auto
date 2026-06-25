package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import java.io.BufferedWriter
import java.nio.file.Files


/**
 * The CSV output stage as a Job Worker (analogue of `CompressedExportWriter`, minus compression). When
 * [header] is true the column names are written once (from the first batch) before the records; when false
 * (a headerless round-trip) only records are written. Fields are written with RFC-4180 quoting that is
 * DELIMITER-AWARE: a field is quoted when it contains the [delimiter], a quote, or a line break, and quotes
 * are escaped by doubling (the same rules as `FlatFileRecord.writeCsvField`, but parameterized on the
 * configured delimiter rather than hard-coding the comma — so a `;`-delimited round-trip is correct).
 *
 * A [SinkWorker]: the framework owns the drain loop, per-batch checkpoint, and throttled written-row progress.
 * The file is opened in [onStart] and closed in [onClose] (so it is flushed and closed on completion, failure,
 * and cancel alike), both via [JobControl.runBlockingIo] / a quick blocking close so the IO stays counted.
 *
 * If every record was filtered out upstream no batch arrives, so the file is left empty (the header is only
 * known from a batch) — acceptable.
 */
@Reflect
class CsvWriterWorker(
    input: ChannelInput<Any?>,

    private val path: String,
    private val delimiter: String,
    private val header: Boolean,

    selfLocation: ObjectLocation
):
    SinkWorker<RecordBatch>(input, selfLocation)
{
    private val delimiterChar: Char =
        if (delimiter.isEmpty()) ',' else delimiter[0]

    private var writer: BufferedWriter? = null
    private var headerWritten = false
    private var written = 0L


    override suspend fun onStart(control: JobControl) {
        writer = control.runBlockingIo { Files.newBufferedWriter(toFilePath(path)) }
    }


    override suspend fun onBatch(batch: RecordBatch, control: JobControl) {
        val writer = writer!!
        control.runBlockingIo {
            if (header && !headerWritten) {
                writeRecord(writer, FlatFileRecord.of(batch.header.values.map { it.text }))
                headerWritten = true
            }
            for (record in batch.records) {
                writeRecord(writer, record)
                written += 1
            }
        }
    }


    override fun onClose() {
        writer?.close()
    }


    override fun progress(snapshot: Any?): Map<String, Any?> =
        mapOf("written" to written)


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
