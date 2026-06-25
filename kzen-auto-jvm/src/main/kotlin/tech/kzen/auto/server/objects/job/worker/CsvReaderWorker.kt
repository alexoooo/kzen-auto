package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
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
 * [FilterWorker], [FormulaWorker]) can still reference columns by name — `c0`, `c1`, … — over headerless data.
 *
 * A [SourceWorker]: the framework owns end-of-stream (closing the output once [produce] returns) and the live
 * row-count progress; this Worker only reads, emits, and closes its own file. File IO runs through
 * [JobControl.runBlockingIo] so the read stays visible to quiescence detection, with a [JobControl.checkpoint]
 * per batch so the read is cooperatively pausable / cancellable.
 */
@Reflect
class CsvReaderWorker(
    output: ChannelOutput<Any?>,

    private val path: String,
    private val delimiter: String,
    private val batch: Int,
    private val header: Boolean,

    selfLocation: ObjectLocation
):
    SourceWorker<RecordBatch>(output, selfLocation)
{
    private var count = 0L


    override suspend fun produce(emit: Emitter<RecordBatch>, control: JobControl) {
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

            var records = ArrayList<FlatFileRecord>(batch)
            if (firstRecord != null) {
                records.add(firstRecord)
                count += 1
            }

            while (true) {
                val record = control.runBlockingIo { csvReader.readRecord() }
                    ?: break

                records.add(record)
                count += 1
                if (records.size >= batch) {
                    // Checkpoint once per emitted batch, NOT per record: the batch is the pipeline's unit of
                    // work, so a pause/cancel lands per batch and a single STEP advances exactly one batch.
                    // (A record-granular checkpoint made stepping/slow-motion advance one invisible record at
                    // a time — thousands of record-steps before any batch surfaced downstream, so stepping
                    // looked "stuck".)
                    control.checkpoint()
                    emit.send(RecordBatch(headerListing, records))
                    publish(control)
                    records = ArrayList(batch)
                }
            }

            if (records.isNotEmpty()) {
                // The trailing partial batch is also a step boundary, so an undersized input (fewer rows than
                // one batch) is still pausable / steppable rather than running straight through.
                control.checkpoint()
                emit.send(RecordBatch(headerListing, records))
            }
        }
        finally {
            csvReader.close()
        }
    }


    override fun progress(snapshot: Any?): Map<String, Any?> =
        mapOf("read" to count)
}
