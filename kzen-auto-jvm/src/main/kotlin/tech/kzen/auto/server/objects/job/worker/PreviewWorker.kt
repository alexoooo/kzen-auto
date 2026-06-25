package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelServer
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


/**
 * A SINK Worker that shows a LIVE sample of the data flowing into it — the interactive replacement for writing
 * to a file. It exercises BOTH UI communication paths, now unified on the framework-owned [snapshot]:
 *
 * - **Trace (push):** a small teaser (the most recent [teaserRows] rows + the running total count) is derived
 *   from the snapshot and published to the Worker's trace as data streams in, for the always-on live view.
 * - **Duplex query (pull):** the Worker answers on-demand slice requests (`offset` / `limit`) from the SAME
 *   snapshot over an (external, UI-facing) duplex Channel ([onQuery]) — the browser→worker request/reply path
 *   for reading a richer sample than the teaser carries.
 *
 * It keeps a ROLLING window of the most recent [sample] records (a live tail — so the sample keeps changing as
 * data flows rather than freezing on the first [sample] records), copied off the hot-path [FlatFileRecord] to
 * `List<String>` (bounded by [sample]). The window is confined to the work coroutine ([onBatch]); after each
 * batch the framework captures an immutable [Snapshot] of it — only that snapshot crosses to the serve
 * coroutine, so no lock / `@Volatile` field is needed here. When the input ends the framework cancels the
 * serve loop so the run reaches a terminal (done) state, and the UI falls back to the final teaser persisted
 * on the trace.
 */
@Reflect
class PreviewWorker(
    input: ChannelInput<Any?>,
    serve: ChannelServer<Any?, Any?>,

    private val sample: Int,
    selfLocation: ObjectLocation
):
    SinkWorker<RecordBatch>(input, selfLocation, serve)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val teaserRows = 50
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Rolling window of the most recent `sample` records (oldest -> newest); the oldest is evicted once full, so
    // the live view keeps sampling fresh data instead of freezing on the first batch. Confined to onBatch.
    private val window = ArrayDeque<List<String>>()
    private var header: List<String> = listOf()
    private var count = 0L


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onBatch(batch: RecordBatch, control: JobControl) {
        if (header.isEmpty() && batch.header.values.isNotEmpty()) {
            header = batch.header.values.map { it.text }
        }

        for (record in batch.records) {
            count += 1
            window.addLast(record.toList())
            if (window.size > sample) {
                window.removeFirst()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun snapshot(): Snapshot =
        Snapshot(header, ArrayList(window), count)


    override fun progress(snapshot: Any?): Map<String, Any?> {
        val snap = snapshot as Snapshot
        return mapOf(
            "header" to snap.header,
            "rows" to snap.rows.takeLast(teaserRows),
            "count" to snap.count)
    }


    override fun onQuery(request: Any?, snapshot: Any?): ExecutionResult {
        val snap = snapshot as? Snapshot
        val executionRequest = request as? ExecutionRequest
        val offset = executionRequest?.getSingle(JobConventions.previewOffsetParameter)
            ?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val limit = executionRequest?.getSingle(JobConventions.previewLimitParameter)
            ?.toIntOrNull()?.coerceAtLeast(0) ?: sample

        val rows = snap?.rows ?: listOf()
        val slice =
            if (offset >= rows.size) {
                listOf()
            }
            else {
                rows.subList(offset, minOf(rows.size, offset + limit)).toList()
            }

        return ExecutionSuccess.ofValue(ExecutionValue.of(mapOf(
            "header" to (snap?.header ?: listOf<String>()),
            "rows" to slice,
            "count" to (snap?.count ?: 0L),
            "offset" to offset.toLong())))
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Immutable point-in-time view of the rolling window, shared with the serve coroutine via WorkerBase.
    class Snapshot(
        val header: List<String>,
        val rows: List<List<String>>,
        val count: Long
    )
}
