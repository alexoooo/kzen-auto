package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import java.nio.file.Files


/**
 * The MULTI-FILE input stage as a Job Worker — reads several delimited text files as ONE combined stream,
 * emitting their records concatenated in `paths` order. The multi-file generalization of [CsvReaderWorker]
 * (same [CsvRecordReader], same full RFC-4180 parsing), the Job analogue of Report's multi-location input.
 *
 * SCHEMA: taken from the FIRST file. When [header] is true the first file's first row names the columns
 * ([HeaderListing]); each SUBSEQUENT file's first row is a header row too and is SKIPPED, its data emitted under
 * the shared schema. When [header] is false no row is a header — the schema is synthesized positionally
 * (`c0, c1, …`, field-count from the first file's first record, so the strictly-typed downstream stages can
 * still reference columns by name) and every row across every file is data. The same immutable [HeaderListing]
 * reference is shared by every emitted [DataRecord]. (Degenerate: an empty first file fixes an empty schema —
 * documented, mirrors [CsvReaderWorker]'s single-file empty behaviour.)
 *
 * Directory browse / glob discovery is the EDITOR's job (P4i `MultiFileInputEditor`, reusing Report's
 * `FileListingAction`); this Worker consumes the already-resolved, ordered list of concrete file paths, which
 * keeps the `(fileIndex, position)` resume cursor deterministic (the file set is fixed config, not re-globbed
 * mid-run). File IO runs through [JobControl.runBlockingIo] so reads stay visible to quiescence detection.
 *
 * STATE MIGRATION: the cursor is `(fileIndex, open-reader-position)`. Only ONE reader is open at a time — the
 * current file's — and it IS the run-scoped state (like [CsvReaderWorker]'s single reader). The source cadence's
 * per-batch checkpoint sits between batches with the output flushed, so a paused reader holds no buffered-but-
 * unsent record. [captureMigrationState] detaches the open reader at its position and carries [fileIndex];
 * [loadMigrationState] re-adopts both — but ONLY if `paths` / `delimiter` / `header` are unchanged — so a pause
 * / edit-config / continue resumes from the exact spot in the current file and reads the remaining files, rather
 * than reopening the whole list from the top. If any of those change, the carried reader is closed and this
 * instance starts fresh from the edited config.
 */
@Reflect
class MultiFileReaderWorker(
    output: ChannelOutput<Any?>,

    private val paths: List<String>,
    private val delimiter: String,
    private val header: Boolean,

    selfLocation: ObjectLocation
):
    SourceWorker<DataRecord>(output, selfLocation)
{
    // Run-scoped cursor + reader state (carried across a migration by capture/loadMigrationState).
    private var fileIndex = 0                            // index into `paths` of the file currently being read
    private var csvReader: CsvRecordReader? = null       // current file's open reader (null between files / at EOF)
    private var headerListing: HeaderListing? = null     // shared schema, resolved once from the first file
    private var pendingFirstRecord: FlatFileRecord? = null  // header=false: first file's first record is data
    private var count = 0L                               // total records emitted across all files
    private var finished = false                         // every file consumed: a resume emits nothing
    private var detached = false                         // reader handed to a migration snapshot: onClose skips it


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun produce(emit: Emitter<DataRecord>, control: JobControl) {
        if (finished) {
            // Resumed after already consuming every file on unchanged config — nothing left to emit.
            return
        }

        while (fileIndex < paths.size) {
            ensureCurrentFileOpen(control)
            val reader = csvReader!!
            val headers = headerListing!!

            // header=false: the first file's first record was read to fix the schema and is itself data.
            pendingFirstRecord?.let {
                emit.send(DataRecord(headers, it))
                count += 1
                pendingFirstRecord = null
            }

            while (true) {
                val record = control.runBlockingIo { reader.readRecord() }
                    ?: break
                emit.send(DataRecord(headers, record))
                count += 1
            }

            // Current file exhausted: close it and advance to the next.
            reader.close()
            csvReader = null
            fileIndex += 1
        }

        finished = true
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Opens paths[fileIndex]; a no-op when resuming with a carried reader. The first file resolves the shared
    // schema; a subsequent file skips its header row (header=true) since the schema is already fixed.
    private suspend fun ensureCurrentFileOpen(control: JobControl) {
        if (csvReader != null) {
            return
        }

        val reader = control.runBlockingIo {
            CsvRecordReader(Files.newBufferedReader(toFilePath(paths[fileIndex])), delimiter)
        }
        csvReader = reader

        if (headerListing == null) {
            // First file overall: the first record names the columns (header=true) or determines the synthesized
            // positional schema AND is itself the first data record (header=false, kept in pendingFirstRecord).
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
        else if (header) {
            // Subsequent file, schema already fixed: discard its header row so it isn't emitted as data.
            control.runBlockingIo { reader.readRecord() }
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
            // Every file consumed, or parked before opening the current file: carry only the logical cursor, no
            // live handle (a resume reopens paths[fileIndex] fresh — nothing of that file was emitted yet).
            return ReaderState(
                null, headerListing, null, count, fileIndex, finished, paths, delimiter, header)
        }

        // Detach the open reader so onClose (during teardown) skips closing it — ownership transfers to the
        // returned state, which JobExecution hands to the rebuilt instance (or closes if the Worker was removed).
        detached = true
        return ReaderState(
            csvReader, headerListing, pendingFirstRecord, count, fileIndex, finished, paths, delimiter, header)
    }


    override fun loadMigrationState(captured: Any?) {
        val state = captured as? ReaderState
            ?: return

        if (state.paths == paths && state.delimiter == delimiter && state.header == header) {
            // Config unchanged: adopt the previous cursor + reader at its position, so reading continues from
            // where it left off (the exact spot in the current file, then the remaining files).
            csvReader = state.reader
            headerListing = state.headerListing
            pendingFirstRecord = state.pendingFirstRecord
            count = state.count
            fileIndex = state.fileIndex
            finished = state.finished
        }
        else {
            // paths / delimiter / header changed: the carried reader points at the wrong file / parse, so close
            // it (teardown skipped closing because it was detached) and start fresh from the edited config.
            state.close()
        }
    }


    override fun progress(snapshot: Any?): Map<String, Any?> =
        mapOf("read" to count, "file" to fileIndex.toLong())


    //-----------------------------------------------------------------------------------------------------------------
    // Immutable migration snapshot of the reader cursor. AutoCloseable so JobExecution can release the detached
    // reader if this Worker was removed by the edit (no rebuilt instance adopts it).
    private class ReaderState(
        val reader: CsvRecordReader?,
        val headerListing: HeaderListing?,
        val pendingFirstRecord: FlatFileRecord?,
        val count: Long,
        val fileIndex: Int,
        val finished: Boolean,
        val paths: List<String>,
        val delimiter: String,
        val header: Boolean
    ): AutoCloseable {
        override fun close() {
            reader?.close()
        }
    }
}
