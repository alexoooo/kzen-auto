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
 * The EXPLORE stage as a Job Worker: a [SinkWorker] with no output that appends every incoming record to a
 * disk-backed [IndexedCsvTable] (Report's substrate-neutral engine — a CSV file + row-offset index, not
 * Report's disruptor pipeline) and answers on-demand preview-slice queries (`offset` / `limit`) against the
 * live table in [onQuery]. The heavy-duty counterpart to [PreviewWorker]: where Preview keeps a bounded
 * in-memory tail, Explore indexes the full stream to disk so the user can page through all of it. Each
 * message's flat part is indexed ([JobMessage.flatView]), so a scalar stream browses as a `value` column.
 * Threading (single-threaded work/serve interleave) and lifecycle follow the base contract — see [WorkerBase]
 * and [SinkWorker].
 *
 * Output dir is PERSISTENT, unlike [PivotWorker]'s transient scratchDir: the table lives at the notation-keyed
 * path from [JobControl.outputDir] and must outlive the run so the result stays browsable / downloadable.
 * Last-run-wins: [onStart] clears the dir, [onClose] flushes-and-closes without deleting. The table is created
 * lazily on the first [onElement] (its constructor needs the stream's header); an empty stream leaves an empty
 * dir and serves an empty preview.
 *
 * Download resolves the same persistent path straight from the Worker's [ObjectLocation], with no live run:
 * [tech.kzen.auto.server.api.handler.DetachedActionHandler.jobDownload] serves `table.csv` at
 * [IndexedCsvTable.tablePath]. This Worker holds no download logic of its own.
 *
 * Live-edit migration is the [WorkerBase] restart default (no state carried): the output path is deterministic
 * per [ObjectLocation], so the rebuilt instance's [onStart] clears and re-indexes at the same path.
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
