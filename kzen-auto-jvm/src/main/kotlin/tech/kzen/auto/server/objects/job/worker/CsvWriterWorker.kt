package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.Worker
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
 * The file is opened / written / closed through `control.runBlockingIo` (so the IO stays counted) inside a
 * `try/finally`, flushed and closed on completion, failure, and cancel alike. Live written-row progress is
 * published to the Worker's trace for the interactive UI.
 *
 * If every record was filtered out upstream no batch arrives, so the file is left empty (the header is only
 * known from a batch) — acceptable.
 */
@Reflect
class CsvWriterWorker(
    private val input: ChannelInput<Any?>,

    private val path: String,
    private val delimiter: String,
    private val header: Boolean,

    private val selfLocation: ObjectLocation
):
    Worker
{
    private val delimiterChar: Char =
        if (delimiter.isEmpty()) ',' else delimiter[0]


    override suspend fun run(control: JobControl) {
        val writer = control.runBlockingIo { Files.newBufferedWriter(toFilePath(path)) }
        var written = 0L
        try {
            var headerWritten = false
            for (item in input) {
                control.checkpoint()
                val batch = item as RecordBatch

                control.runBlockingIo {
                    if (header && ! headerWritten) {
                        writeRecord(writer, FlatFileRecord.of(batch.header.values.map { it.text }))
                        headerWritten = true
                    }
                    for (record in batch.records) {
                        writeRecord(writer, record)
                        written += 1
                    }
                }
                control.publishProgress(selfLocation, mapOf("written" to written))
            }
            control.publishProgress(selfLocation, mapOf("written" to written), force = true)
        }
        finally {
            writer.close()
        }
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


    private fun writeField(writer: BufferedWriter, value: String) {
        var needsQuote = false
        for (character in value) {
            if (character == delimiterChar || character == '"' || character == '\r' || character == '\n') {
                needsQuote = true
                break
            }
        }

        if (! needsQuote) {
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
