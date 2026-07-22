package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.common.objects.document.report.output.OutputPreview
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelServer
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.report.exec.output.flat.IndexedCsvTable
import tech.kzen.auto.server.util.WorkUtils
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import java.nio.file.Files
import java.nio.file.Path


/**
 * The EXPLORE stage as a Job Worker — the disk-backed, random-access browse over the whole result stream, reusing
 * Report's substrate-neutral [IndexedCsvTable] engine (a CSV file + a row-offset index; NOT Report's disruptor
 * pipeline). A [SinkWorker] with no output: every incoming record is appended to the table in [onElement], and
 * the browser reads ANY window of the accumulated result — no matter how large — via on-demand slice queries
 * (`offset` / `limit`) answered from the LIVE table in [onQuery]. It is the Job analogue of Report's Explore
 * output, and the heavy-duty counterpart to [PreviewWorker]: where Preview keeps a bounded in-memory live tail,
 * Explore indexes the FULL tabular stream to disk so the user can page through all of it. It indexes each
 * message's flat part (a payload-lane message auto-flattens — [JobMessage.flatView] — so a scalar stream
 * browses as a `value` column).
 *
 * OUTPUT DIR (PERSISTENT): [IndexedCsvTable] is file-backed, so it opens under the per-Worker directory from
 * [JobControl.outputDir] (see [tech.kzen.auto.server.objects.job.service.JobWorkPool]) — but, UNLIKE
 * [PivotWorker]'s transient `scratchDir`, this is the Worker's PERSISTENT, notation-keyed output that must
 * OUTLIVE the run so the result stays browsable / downloadable afterward (a Job used for reporting — otherwise
 * the report is useless the instant the run settles). Semantics are last-run-wins: [onStart] CLEARS the dir so
 * this run fully replaces the previous run's table, and [onClose] FLUSHES-and-closes it (keeping the files) —
 * it never deletes. The header is only known once the first record arrives, so the table is created lazily on
 * the first [onElement] (its constructor writes the header row); an empty stream leaves an empty dir (and serves
 * an empty preview, with no downloadable table).
 *
 * INTERACTIVITY (via [SinkWorker]'s optional `serve` port): answers on-demand preview-slice queries against the
 * LIVE table ([onQuery]) while the run is active. Reading the disk-backed table from the serve coroutine is
 * race-free because a Worker runs SINGLE-THREADED on its own node coroutine (see
 * [tech.kzen.auto.server.exec.job.EngineJobControl]): the serve loop only runs while the work coroutine is
 * parked at a checkpoint / awaiting input, never concurrently with an [onElement] append. [IndexedCsvTable]
 * interleaves append and random-access read on the same handle by design (`preview` flushes pending writes,
 * then seeks to read), so a query mid-stream sees every appended row. A running row count is pushed to the trace.
 *
 * DOWNLOAD (the Job analogue of Report's `DetachedDownloadAction`): because the table lives at a NOTATION-keyed
 * path that survives the run, [tech.kzen.auto.server.api.RestHandler.jobDownload] resolves it straight from the
 * Worker's [ObjectLocation] — with NO live run — and streams `table.csv` (via [IndexedCsvTable.downloadCsvOffline]).
 * So the report downloads AFTER the run ends (post-settle the on-disk file is complete — [onClose] flushed it);
 * during a live run the same endpoint streams the rows flushed so far. This Worker holds no download logic of
 * its own (the serve port is only for live preview slices).
 *
 * LIVE-EDIT MIGRATION: P4 baseline is RESTART on a live edit (the [WorkerBase] default — no state carried). The
 * output path is deterministic per [ObjectLocation] (stable across the rebuild), so the OUTGOING instance's
 * [onClose] flushes-and-closes the table and the rebuilt instance's [onStart] clears + re-indexes into a fresh
 * table at the same path — the same fresh-table restart as before, just with the delete moved from teardown to
 * the rebuild's start (so the final settle keeps the data). Carrying the table forward across an edit (like
 * [tech.kzen.auto.server.objects.job.worker.CsvReaderWorker] carries its reader) is a documented later extension.
 */
@Reflect
class ExploreWorker(
    input: ChannelInput<Any?>,
    serve: ChannelServer<Any?, Any?>,
    selfLocation: ObjectLocation
):
    SinkWorker(input, selfLocation, serve)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // Default preview slice served when a pull query omits a limit (mirrors PivotWorker's default).
        private const val defaultQueryLimit = 1000
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Created lazily on the first record (its constructor needs the stream's header) under the persistent output
    // dir; flushed-and-closed (NOT deleted) in onClose. Confined to the work coroutine except for the immutable
    // [ExploreView] handle the serve coroutine reads (single-threaded — safe; see the class doc).
    private var table: IndexedCsvTable? = null
    private var outputDir: Path? = null
    private var count = 0L


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onStart(control: JobControl) {
        // The persistent, notation-keyed output dir survives across runs, so start last-run-wins: drop any prior
        // run's table and recreate an empty dir. The table itself waits for the first record's header.
        val dir = Path.of(control.outputDir())
        if (Files.exists(dir)) {
            WorkUtils.recursivelyDeleteDir(dir)
        }
        Files.createDirectories(dir)
        outputDir = dir
    }


    override suspend fun onElement(element: JobMessage, control: JobControl) {
        val flat = element.flatView()
        val header = flat.header
        val activeTable = table
            ?: control
                .runBlockingIo { IndexedCsvTable(header, outputDir!!) }
                .also { table = it }

        activeTable.add(flat.record, header)
        count += 1
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun snapshot(): Any? {
        val activeTable = table
            ?: return null
        return ExploreView(activeTable, count)
    }


    override fun progress(snapshot: Any?): Map<String, Any?> {
        // The count key (matching PreviewWorker) so the client parses it as the total row count and can gate
        // the download link on there being data to download.
        val view = snapshot as? ExploreView
        return mapOf(JobConventions.progressCountKey to (view?.rowCount ?: 0L))
    }


    override fun onQuery(request: Any?, snapshot: Any?): ExecutionResult {
        val view = snapshot as? ExploreView
            ?: return ExecutionSuccess.ofValue(ExecutionValue.of(emptyPreview().asCollection()))

        val executionRequest = request as? ExecutionRequest

        val offset = executionRequest?.getSingle(JobConventions.previewOffsetParameter)
            ?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        val limit = executionRequest?.getSingle(JobConventions.previewLimitParameter)
            ?.toIntOrNull()?.coerceAtLeast(0) ?: defaultQueryLimit

        val preview = view.table.preview(offset, limit)
        return ExecutionSuccess.ofValue(ExecutionValue.of(preview.asCollection()))
    }


    // Before the first record the header is unknown, so an empty stream serves a header-less empty preview.
    private fun emptyPreview(): OutputPreview =
        OutputPreview(listOf(), listOf(), 0L)


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClose() {
        // Flush-and-close, KEEP the files: the output dir is persistent (last-run-wins), so the result stays on
        // disk to be browsed / downloaded after the run settles. `error = false` flushes pending rows first, so
        // the post-run table.csv is complete. onStart clears the dir on the next run.
        table?.close(error = false)
        table = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Immutable handle crossing to the serve coroutine. It wraps the LIVE table rather than a materialized copy
    // (the table is disk-backed and can be large) — safe because the Worker is single-threaded, so onQuery only
    // reads the table while the work coroutine is parked (see the class doc).
    class ExploreView(
        val table: IndexedCsvTable,
        val rowCount: Long
    )
}
