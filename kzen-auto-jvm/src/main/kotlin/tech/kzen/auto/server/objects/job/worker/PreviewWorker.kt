package tech.kzen.auto.server.objects.job.worker

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelServer
import tech.kzen.auto.common.paradigm.job.api.Worker
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


/**
 * A SINK Worker that shows a LIVE sample of the data flowing into it — the interactive replacement for
 * writing to a file. It exercises BOTH UI communication paths a Worker can use:
 *
 * - **Trace (push):** a small teaser (the most recent [teaserRows] rows + the running total count) is
 *   published to the Worker's trace as data streams in, for the always-on live view the UI polls.
 * - **Duplex query (pull):** the Worker SERVES an (external, UI-facing) duplex Channel ([serve]), answering
 *   on-demand slice requests (`offset` / `limit`) over its larger buffered sample — the browser→worker
 *   request/reply path for reading richer internal state than the teaser carries.
 *
 * It keeps a ROLLING window of the most recent [sample] records (a live tail — so the sample keeps changing
 * as data flows rather than freezing on the first [sample] records seen), copied off the hot-path
 * [tech.kzen.auto.plugin.model.record.FlatFileRecord] to `List<String>` (bounded by [sample]) plus the
 * running count, published as `@Volatile` immutable snapshots (single writer — the input loop — so a volatile
 * publish suffices, no lock). The input drain and the serve loop run as two child coroutines under one
 * `coroutineScope`; when the input ends the serve loop is cancelled so the run reaches a terminal (done)
 * state, and the UI falls back to the final teaser persisted on the trace (Report's online/offline pattern).
 */
@Reflect
class PreviewWorker(
    private val input: ChannelInput<Any?>,
    private val serve: ChannelServer<Any?, Any?>,

    private val sample: Int,
    private val selfLocation: ObjectLocation
):
    Worker
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val teaserRows = 50
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Volatile private var previewHeader: List<String> = listOf()
    @Volatile private var previewRows: List<List<String>> = listOf()
    @Volatile private var previewCount: Long = 0L


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun run(control: JobControl): Unit = coroutineScope {
        val serveJob = launch {
            for (served in serve) {
                served.reply(handleQuery(served.request))
            }
        }

        try {
            // Rolling window of the most recent `sample` records (oldest -> newest); the oldest is evicted
            // once full, so the live view keeps sampling fresh data instead of freezing on the first batch.
            val window = ArrayDeque<List<String>>()
            var count = 0L

            for (item in input) {
                control.checkpoint()
                val batch = item as RecordBatch

                if (previewHeader.isEmpty() && batch.header.values.isNotEmpty()) {
                    previewHeader = batch.header.values.map { it.text }
                }

                for (record in batch.records) {
                    count += 1
                    window.addLast(record.toList())
                    if (window.size > sample) {
                        window.removeFirst()
                    }
                }
                previewRows = ArrayList(window)
                previewCount = count

                publishTeaser(control, force = false)
            }

            publishTeaser(control, force = true)
        }
        finally {
            serveJob.cancel()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun publishTeaser(control: JobControl, force: Boolean) {
        control.publishProgress(
            selfLocation,
            mapOf(
                "header" to previewHeader,
                "rows" to previewRows.takeLast(teaserRows),
                "count" to previewCount),
            force)
    }


    private fun handleQuery(request: Any?): ExecutionResult {
        val executionRequest = request as? ExecutionRequest
        val offset = executionRequest?.getSingle(JobConventions.previewOffsetParameter)
            ?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val limit = executionRequest?.getSingle(JobConventions.previewLimitParameter)
            ?.toIntOrNull()?.coerceAtLeast(0) ?: sample

        val rows = previewRows
        val slice =
            if (offset >= rows.size) {
                listOf()
            }
            else {
                rows.subList(offset, minOf(rows.size, offset + limit)).toList()
            }

        return ExecutionSuccess.ofValue(ExecutionValue.of(mapOf(
            "header" to previewHeader,
            "rows" to slice,
            "count" to previewCount,
            "offset" to offset.toLong())))
    }
}
