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
 * It consumes the untyped `Any?` input lane so one live view serves every stream: a [DataRecord] (the CSV
 * lane) renders column-for-column, while any other element (a scalar from a FormulaSource / Run lane — e.g. a
 * FizzBuzz `String`) renders as a single `value` column.
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
    input: ChannelInput<Any?>,
    serve: ChannelServer<Any?, Any?>,

    private val sample: Int,
    selfLocation: ObjectLocation
):
    SinkWorker<Any?>(input, selfLocation, serve)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // Synthetic column name for the scalar lane (a non-RecordBatch element rendered as a one-column row).
        private const val scalarColumn = "value"
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Rolling window of the most recent `sample` records (oldest -> newest); the oldest is evicted once full, so
    // the live view keeps sampling fresh data instead of freezing on the first batch. Confined to onBatch.
    private val window = ArrayDeque<List<String>>()
    private var header: List<String> = listOf()
    private var count = 0L


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onElement(element: Any?, control: JobControl) {
        when (element) {
            // CSV lane: a typed record, rendered column-for-column under its header.
            is DataRecord -> {
                if (header.isEmpty() && element.header.values.isNotEmpty()) {
                    header = element.header.values.map { it.text }
                }
                addRow(element.record.toList())
            }

            // Scalar lane: a single arbitrary element (e.g. a FizzBuzz String from a Run Worker), rendered as
            // one `value` column so the same live view works for non-record streams.
            else -> {
                if (header.isEmpty()) {
                    header = listOf(scalarColumn)
                }
                addRow(listOf(element?.toString() ?: ""))
            }
        }
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


    override fun progress(snapshot: Any?): Map<String, Any?> {
        val snap = snapshot as Snapshot
        return mapOf(
            "header" to snap.header,
            "rows" to snap.rows,
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
            "header" to (snap?.header ?: listOf()),
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
