package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelServer
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.DataState
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
 * - **Trace (push):** a small teaser (the most recent [JobConventions.progressTeaserRowCount] rows + the
 *   running total count) is derived from the snapshot and published to the Worker's trace as data streams in,
 *   for the always-on live view; the final (forced) end-of-stream push carries the full window instead, so
 *   the post-run card keeps the whole sample.
 * - **Duplex query (pull):** the Worker answers on-demand slice requests (`offset` / `limit`) from the SAME
 *   snapshot over an (external, UI-facing) duplex Channel ([onQuery]) — the browser→worker request/reply path
 *   for reading a richer sample than the teaser carries.
 *
 * One live view serves every stream via the message's flat part: a flat-part message (the CSV lane) renders
 * column-for-column, while a payload-lane message (a scalar from a FormulaSource / Run lane — e.g. a FizzBuzz
 * `String`) projects to a single synthetic `value` column.
 *
 * It keeps a ROLLING window of the most recent [sample] records (a live tail — so the sample keeps changing as
 * data flows rather than freezing on the first [sample] records), copied off the hot-path [FlatFileRecord] to
 * `List<String>` (bounded by [sample]). The window is confined to the work coroutine ([onElement]); after each
 * element the framework captures an immutable [Snapshot] of it — only that snapshot crosses to the serve
 * coroutine, so no lock / `@Volatile` field is needed here. When the input ends the framework cancels the
 * serve loop so the run reaches a terminal (done) state, and the UI falls back to the final teaser persisted
 * on the trace.
 */
@Reflect
class PreviewWorker(
    input: ChannelInput<*>,
    serve: ChannelServer<Any?, Any?>,

    private val sample: Int,
    selfLocation: ObjectLocation
):
    SinkWorker(input, selfLocation, serve)
{
    //-----------------------------------------------------------------------------------------------------------------
    // Rolling window of the most recent `sample` records (oldest -> newest); the oldest is evicted once full, so
    // the live view keeps sampling fresh data instead of freezing on the first batch. Confined to onBatch.
    private val window = ArrayDeque<List<String>>()
    private var header: List<String> = listOf()
    private var count = 0L


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onElement(element: DataValue, control: JobControl) {
        if (element.access.state(element.root) == DataState.Null) {
            if (header.isEmpty()) {
                header = listOf("value")
            }
            addRow(listOf("null"))
            return
        }
        // The flat part is the one rendering lane: the CSV lane renders column-for-column under its header, a
        // payload lane auto-flattens to the shared `value` column (or a Map payload's keyed columns).
        val projection = JobDataValues.projection(element)
        val elementHeader = projection.header
        if (header.isEmpty() && elementHeader.values.isNotEmpty()) {
            header = elementHeader.values.map { it.text }
        }
        addRow((0 until projection.size).map(projection::render))
    }


    private fun addRow(row: List<String>) {
        count += 1
        window.addLast(row)
        if (window.size > sample) {
            window.removeFirst()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Opt into state migration: when a pause / edit-config / continue rebuilds the Job graph, carry the live
    // sample (header + rolling window + running count) forward into the rebuilt Preview so the view doesn't
    // reset to empty across the edit. The snapshot is an immutable copy (no live handle) — coherent because the
    // Preview accumulates and never re-truncates; over-counts only if the upstream source RESTARTS rather than
    // resumes (with CsvReaderWorker resuming from position on unchanged config, reader -> preview stays exact).
    override fun captureMigrationState(): Any =
        snapshot()


    override fun loadMigrationState(captured: Any?) {
        val snap = captured as Snapshot
        header = snap.header
        count = snap.count
        window.clear()
        window.addAll(snap.rows)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun snapshot(): Snapshot =
        Snapshot(header, ArrayList(window), count)


    // Push is a teaser, pull is the payload: periodic pushes carry only the most recent teaser rows (the
    // window is oldest -> newest), while the final forced push keeps the full window for the post-run card.
    override fun progress(snapshot: Any?, force: Boolean): Map<String, Any?> {
        val snap = snapshot as Snapshot
        val rows =
            if (force) {
                snap.rows
            }
            else {
                snap.rows.takeLast(JobConventions.progressTeaserRowCount)
            }
        return mapOf(
            JobConventions.progressHeaderKey to snap.header,
            JobConventions.progressRowsKey to rows,
            JobConventions.progressCountKey to snap.count)
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
            JobConventions.progressHeaderKey to (snap?.header ?: listOf()),
            JobConventions.progressRowsKey to slice,
            JobConventions.progressCountKey to (snap?.count ?: 0L),
            JobConventions.previewOffsetParameter to offset.toLong())))
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Immutable point-in-time view of the rolling window, shared with the serve coroutine via WorkerBase.
    class Snapshot(
        val header: List<String>,
        val rows: List<List<String>>,
        val count: Long
    )
}
