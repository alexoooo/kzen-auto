package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.output.OutputPivotExportSignature
import tech.kzen.auto.common.objects.document.report.output.OutputPreview
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotSpec
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotValueTableSpec
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.api.ChannelServer
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.objects.report.exec.output.pivot.PivotBuilder
import tech.kzen.auto.server.util.WorkUtils
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import java.nio.file.Path


/**
 * The pivot-table analytics stage as a Job Worker — reusing Report's substrate-neutral [PivotBuilder] engine
 * (the disk-backed row index + value statistics; NOT Report's disruptor pipeline). It ACCUMULATES every incoming
 * record into the pivot in [onElement], then on end-of-stream ([onComplete]) EMITS the built pivot table
 * downstream row-by-row as [DataRecord]s under a stable [outputHeader] (row-key columns + one column per
 * value/type), so it composes into any pipeline (`reader → pivot → writer` / `→ preview`) — an arbitrary DAG
 * replacing Report's fixed `analysis → output` coupling.
 *
 * SCRATCH DIR: unlike [SummaryWorker] (bounded in-memory), [PivotBuilder]'s H2-backed stores need an on-disk
 * directory, obtained from [JobControl.scratchDir] (per-Worker, run-scoped — see
 * [tech.kzen.auto.server.objects.job.service.JobWorkPool]). [onClose] does CLOSE-THEN-DELETE: the H2 stores hold
 * a Windows file lock, so the builder MUST be closed before its dir can be removed; the engine's run-root sweep
 * ([tech.kzen.auto.server.exec.job.JobRun]) is the belt-and-suspenders backstop for a hard kill.
 *
 * INTERACTIVITY (via [TransformWorker]'s optional `serve` port): answers on-demand preview-slice queries
 * (`offset` / `limit`) against the LIVE pivot ([onQuery]) — the Job analogue of Report's pivot preview. Reading
 * the live disk-backed builder from the serve coroutine is race-free because a Worker runs SINGLE-THREADED on
 * its own node coroutine (see [tech.kzen.auto.server.exec.job.EngineJobControl]): the serve loop only runs while
 * the work coroutine is parked at a checkpoint / awaiting input, never concurrently with an [onElement] mutation.
 * A running row count plus a bounded teaser page of the live pivot is pushed to the trace (see [progress]).
 *
 * LIVE-EDIT MIGRATION: P4 baseline is RESTART on a live edit (the [WorkerBase] default — no state carried), which
 * is coherent because the scratch path is deterministic per `(runId, stableId)` and runId is migrate-stable, so
 * the rebuilt instance rebuilds the pivot from a resuming upstream reader. Carrying the store forward (like
 * [tech.kzen.auto.server.objects.job.worker.CsvReaderWorker] carries its reader) is a documented later extension.
 */
@Reflect
class PivotWorker(
    input: ChannelInput<Any?>,
    output: ChannelOutput<Any?>,
    serve: ChannelServer<Any?, Any?>,
    private val pivot: PivotSpec,
    selfLocation: ObjectLocation
):
    TransformWorker<DataRecord, DataRecord>(input, output, selfLocation, serve)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // Rows read per traversal page when emitting the built pivot downstream (bounds the emit conversion's peak
        // memory — the whole pivot never materializes as one List).
        private const val emitPageSize = 1024

        // Default preview slice served when a pull query omits a limit (mirrors PreviewWorker's sample default).
        private const val defaultQueryLimit = 1000
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The value columns to aggregate (the keys of the values spec) — PivotBuilder.create wants them as a listing.
    private val valueColumns = HeaderListing(pivot.values.columns.keys.toList())

    // The output table's header (row-key columns + one column per value/type), stable for every emitted record.
    private val exportSignature = OutputPivotExportSignature.of(pivot.rows, pivot.values)
    private val outputHeader = HeaderListing.of(exportSignature.header.map { it.render() })

    // Opened in onStart (needs the scratch dir from control), closed-and-deleted in onClose. Confined to the work
    // coroutine except for the immutable [PivotView] handle the serve coroutine reads (single-threaded — safe).
    private var pivotBuilder: PivotBuilder? = null
    private var scratchDir: Path? = null


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onStart(control: JobControl) {
        val dir = Path.of(control.scratchDir())
        scratchDir = dir
        pivotBuilder = control.runBlockingIo {
            PivotBuilder.create(pivot.rows, valueColumns, dir)
        }
    }


    override suspend fun onElement(element: DataRecord, emit: Emitter<DataRecord>, control: JobControl) {
        // Accumulate only: the pivot table is emitted once, at end-of-stream (onComplete).
        pivotBuilder!!.add(element.record, element.header)
    }


    override suspend fun onComplete(emit: Emitter<DataRecord>, control: JobControl) {
        val builder = pivotBuilder!!
        val total = builder.rowCount()

        var start = 0L
        while (start < total) {
            val preview = builder.preview(pivot.values, start, emitPageSize)
            if (preview.rows.isEmpty()) {
                break
            }
            for (row in preview.rows) {
                emit.send(DataRecord(outputHeader, FlatFileRecord.of(row)))
            }
            start += preview.rows.size
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun snapshot(): Any? {
        val builder = pivotBuilder
            ?: return null
        return PivotView(builder, pivot.values, builder.rowCount())
    }


    // Push is a teaser, pull is the payload: periodic pushes carry the running row count plus a bounded first
    // page of the live pivot (matching what PreviewWorkerDisplay renders); the final forced push carries up to
    // [defaultQueryLimit] rows for the post-run card. Reading the live builder here is safe for the same
    // reason [onQuery] does it (single-threaded Worker; the final forced publish runs before [onClose]).
    override fun progress(snapshot: Any?, force: Boolean): Map<String, Any?> {
        val view = snapshot as? PivotView
            ?: return mapOf(JobConventions.progressCountKey to 0L)

        val limit = if (force) defaultQueryLimit else JobConventions.progressTeaserRowCount
        val preview = view.builder.preview(view.values, 0, limit)
        return mapOf(
            JobConventions.progressCountKey to view.rowCount,
            JobConventions.progressHeaderKey to preview.renderedHeader,
            JobConventions.progressRowsKey to preview.rows)
    }


    override fun onQuery(request: Any?, snapshot: Any?): ExecutionResult {
        val view = snapshot as? PivotView
            ?: return ExecutionSuccess.ofValue(ExecutionValue.of(emptyPreview().asCollection()))

        val executionRequest = request as? ExecutionRequest
        val offset = executionRequest?.getSingle(JobConventions.previewOffsetParameter)
            ?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        val limit = executionRequest?.getSingle(JobConventions.previewLimitParameter)
            ?.toIntOrNull()?.coerceAtLeast(0) ?: defaultQueryLimit

        val preview = view.builder.preview(view.values, offset, limit)
        return ExecutionSuccess.ofValue(ExecutionValue.of(preview.asCollection()))
    }


    private fun emptyPreview(): OutputPreview =
        OutputPreview(exportSignature.header.map { it.render() }, listOf(), 0L)


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClose() {
        // Close-then-delete: the H2 stores hold a Windows file lock, so the store MUST be closed before its
        // scratch dir can be removed.
        try {
            pivotBuilder?.close()
        }
        finally {
            scratchDir?.let { WorkUtils.recursivelyDeleteDir(it) }
        }
        pivotBuilder = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Immutable handle crossing to the serve coroutine. It wraps the LIVE builder rather than a materialized copy
    // (the pivot can be large and disk-backed) — safe because the Worker is single-threaded, so onQuery only reads
    // the builder while the work coroutine is parked (see the class doc).
    class PivotView(
        val builder: PivotBuilder,
        val values: PivotValueTableSpec,
        val rowCount: Long
    )
}
