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
 *
 * STATE MIGRATION: the open reader IS run-scoped state. The [checkpoint] sits at the TOP of the batch loop, so a
 * paused reader holds no built-but-unsent batch; a batch it has built and is parked mid-`send` on rides the
 * OUTPUT channel's own in-flight capture ([tech.kzen.auto.server.objects.job.channel.JobChannel.drainBuffered]),
 * not this Worker. [captureMigrationState] detaches the open reader at its current file position and
 * [loadMigrationState] re-adopts it — but ONLY if `path` / `delimiter` / `header` are unchanged — so a pause /
 * edit-config / continue continues reading from where it left off instead of reopening and re-reading the file
 * from the top. If those change, the carried reader is closed and this one opens the new file fresh.
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
    // Run-scoped reader state (null until opened; carried across a migration by capture/loadMigrationState).
    private var csvReader: CsvRecordReader? = null
    private var headerListing: HeaderListing? = null
    private var pendingFirstRecord: FlatFileRecord? = null  // header=false: first record is also first data row
    private var count = 0L
    private var finished = false   // reached EOF: a resume emits nothing rather than reopening + re-reading
    private var detached = false   // reader handed to a migration snapshot: onClose must NOT close it


    override suspend fun produce(emit: Emitter<RecordBatch>, control: JobControl) {
        if (finished) {
            // Resumed after already reaching EOF on unchanged config — nothing left to emit.
            return
        }

        ensureOpen(control)
        val reader = csvReader!!
        val headers = headerListing!!

        while (true) {
            // Checkpoint at the batch boundary BEFORE reading, so a paused reader holds no built-but-unsent
            // batch — its only resumable position is the open reader. A batch already built and parked mid-send
            // is captured by the output channel, not here. One step still surfaces exactly one batch downstream.
            control.checkpoint()

            val records = ArrayList<FlatFileRecord>(batch)
            pendingFirstRecord?.let {
                records.add(it)
                count += 1
                pendingFirstRecord = null
            }
            while (records.size < batch) {
                val record = control.runBlockingIo { reader.readRecord() }
                    ?: break
                records.add(record)
                count += 1
            }

            if (records.isEmpty()) {
                break
            }

            emit.send(RecordBatch(headers, records))
            publish(control)

            if (records.size < batch) {
                break  // a partial batch means EOF was reached
            }
        }

        finished = true
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Opens the file and resolves the header schema once; a no-op when resuming with a carried reader.
    private suspend fun ensureOpen(control: JobControl) {
        if (csvReader != null) {
            return
        }

        val reader = control.runBlockingIo {
            CsvRecordReader(Files.newBufferedReader(toFilePath(path)), delimiter)
        }
        csvReader = reader

        // The first record names the columns when header=true, otherwise it determines the synthesized
        // positional schema AND is itself the first data record (kept in pendingFirstRecord).
        val first = control.runBlockingIo { reader.readRecord() }
        headerListing = when {
            header ->
                if (first == null) HeaderListing.empty else HeaderListing.of(first.toList())

            first == null ->
                HeaderListing.empty

            else -> {
                pendingFirstRecord = first
                HeaderListing.of((0 until first.fieldCount()).map { "c$it" })
            }
        }
    }


    override fun onClose() {
        // Skip closing a reader that was handed to a migration snapshot (it lives on in the rebuilt instance).
        if (! detached) {
            csvReader?.close()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun captureMigrationState(): Any {
        if (finished || csvReader == null) {
            // EOF already reached (file closed) or never opened: carry only the logical markers, no live handle.
            return ReaderState(
                null, headerListing, null, count, path, delimiter, header, finished)
        }

        // Detach the open reader so onClose (during teardown) skips closing it — ownership transfers to the
        // returned state, which JobExecution hands to the rebuilt instance (or closes if the Worker was removed).
        detached = true
        return ReaderState(
            csvReader, headerListing, pendingFirstRecord, count, path, delimiter, header, finished)
    }


    override fun loadMigrationState(captured: Any?) {
        val state = captured as? ReaderState
            ?: return

        if (state.path == path && state.delimiter == delimiter && state.header == header) {
            // Config unchanged: adopt the previous reader at its position (or its EOF marker), so reading
            // continues from where it left off instead of reopening + re-reading the file from the top.
            csvReader = state.reader
            headerListing = state.headerListing
            pendingFirstRecord = state.pendingFirstRecord
            count = state.count
            finished = state.finished
            // TODO: communicate resumption status to the UI — surface (e.g. on this worker's progress trace)
            //  that a migration occurred AND that the reader RESUMED from row `count` (rather than restarting),
            //  so the user can see that their edit preserved progress. Pair with the restart branch below.
        }
        else {
            // path / delimiter / header changed: the carried reader points at the wrong file / parse, so close
            // it (teardown skipped closing because it was detached) and start fresh from the edited config.
            // TODO: communicate to the UI that the reader RESTARTED from the top because path/delimiter/header
            //  changed (resumption did NOT happen), so the user understands why progress was not preserved.
            state.close()
        }
    }


    override fun progress(snapshot: Any?): Map<String, Any?> =
        mapOf("read" to count)


    //-----------------------------------------------------------------------------------------------------------------
    // Immutable migration snapshot of the reader's run-scoped state. AutoCloseable so JobExecution can release
    // the detached reader if this Worker was removed by the edit (no rebuilt instance adopts it).
    private class ReaderState(
        val reader: CsvRecordReader?,
        val headerListing: HeaderListing?,
        val pendingFirstRecord: FlatFileRecord?,
        val count: Long,
        val path: String,
        val delimiter: String,
        val header: Boolean,
        val finished: Boolean
    ): AutoCloseable {
        override fun close() {
            reader?.close()
        }
    }
}
