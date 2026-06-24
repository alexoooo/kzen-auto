package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.api.Worker
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import java.nio.file.Files


/**
 * The CSV input stage as a Job Worker (analogue of `ReportInputReader`, reimplemented Job-native). Reads a
 * delimited text file with full RFC-4180 parsing (quoted fields, embedded delimiters / newlines, doubled
 * quotes) via [CsvRecordReader], emitting [FlatFileRecord]s in fixed-size [batch]es ([RecordBatch]).
 *
 * When [header] is true the first record names the columns. When false (a headerless file, e.g. the 1BRC
 * measurement set) every record is data; the schema is then SYNTHESIZED as positional names `c0, c1, …`
 * (field-count taken from the first record), so the strictly-typed downstream stages (the expression
 * [FilterWorker], [FormulaWorker]) can still reference columns by name — `c0`, `c1`, … — over headerless
 * data.
 *
 * File IO runs through `control.runBlockingIo` so the read stays visible to quiescence detection; a
 * `checkpoint()` per batch makes it cooperatively pausable / cancellable; the `try/finally` closes the file
 * (and propagates EOF by closing the output) on completion, failure, and cancel alike. Live row-count
 * progress is published to the Worker's trace for the interactive UI.
 */
@Reflect
class CsvReaderWorker(
    private val output: ChannelOutput<Any?>,

    private val path: String,
    private val delimiter: String,
    private val batch: Int,
    private val header: Boolean,

    private val selfLocation: ObjectLocation
):
    Worker
{
    override suspend fun run(control: JobControl) {
        val csvReader = control.runBlockingIo {
            CsvRecordReader(Files.newBufferedReader(toFilePath(path)), delimiter)
        }
        try {
            // Read the first record up front: it names the columns when header=true, otherwise it determines
            // the synthesized positional schema AND is itself the first data record.
            var firstRecord = control.runBlockingIo { csvReader.readRecord() }

            val headerListing =
                if (header) {
                    val first = firstRecord ?: return  // empty file: nothing to emit
                    firstRecord = null
                    HeaderListing.of(first.toList())
                }
                else if (firstRecord == null) {
                    HeaderListing.empty
                }
                else {
                    HeaderListing.of((0 until firstRecord.fieldCount()).map { "c$it" })
                }

            var count = 0L
            var records = ArrayList<FlatFileRecord>(batch)
            if (firstRecord != null) {
                records.add(firstRecord)
                count += 1
            }

            while (true) {
                control.checkpoint()
                val record = control.runBlockingIo { csvReader.readRecord() }
                    ?: break

                records.add(record)
                count += 1
                if (records.size >= batch) {
                    output.send(RecordBatch(headerListing, records))
                    control.publishProgress(selfLocation, mapOf("read" to count))
                    records = ArrayList(batch)
                }
            }

            if (records.isNotEmpty()) {
                output.send(RecordBatch(headerListing, records))
            }
            control.publishProgress(selfLocation, mapOf("read" to count), force = true)
        }
        finally {
            csvReader.close()
            output.close()
        }
    }
}
