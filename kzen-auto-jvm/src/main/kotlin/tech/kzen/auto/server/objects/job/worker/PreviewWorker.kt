package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.api.ChannelServer
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.common.objects.document.job.preview.PreviewNode
import tech.kzen.auto.common.objects.document.job.preview.PreviewTable
import tech.kzen.auto.server.objects.job.worker.preview.PreviewCapture
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


/**
 * A rolling, detached display sample shared by trace pushes, duplex queries and migration, taken from values that
 * pass through unchanged: [output] is optional, so a Preview can sit between any two Workers or end a Job. With
 * nothing consuming its output, the sampled values are dropped after the callback, as a sink's are.
 */
@Reflect
class PreviewWorker(
    input: ChannelInput<*>,
    private val output: ChannelOutput<DataValue>?,
    serve: ChannelServer<Any?, Any?>,

    private val sample: Int,
    selfLocation: ObjectLocation
):
    TransformWorker(input, output ?: Unconsumed, selfLocation, serve)
{
    private val window = ArrayDeque<PreviewNode>()
    private var count = 0L

    override suspend fun onElement(element: DataValue, emit: Emitter, control: JobControl) {
        count++
        window.addLast(PreviewCapture.capture(element))
        while (window.size > sample.coerceAtLeast(0)) {
            window.removeFirst()
        }
        if (output != null) {
            emit.send(element)
        }
    }

    override fun captureMigrationState(): Any = snapshot()

    override fun loadMigrationState(captured: Any?) {
        val snap = captured as Snapshot
        count = snap.count
        window.clear()
        window.addAll(snap.items.takeLast(sample.coerceAtLeast(0)))
    }

    override fun snapshot(): Snapshot = Snapshot(window.toList(), count)

    override fun progress(snapshot: Any?, force: Boolean): Map<String, Any?> {
        val snap = snapshot as Snapshot
        val items = if (force) snap.items else snap.items.takeLast(JobConventions.progressTeaserRowCount)
        return wire(items, snap.count)
    }

    private fun wire(items: List<PreviewNode>, count: Long): Map<String, Any?> {
        val table = PreviewTable(items)
        return mapOf(
            JobConventions.progressHeaderKey to table.columns.map { it.label },
            JobConventions.progressRowsKey to table.rows.map { row -> row.map { it.text } },
            JobConventions.progressCountKey to count,
            PreviewNode.progressKey to items.map { it.encode() })
    }

    override fun onQuery(request: Any?, snapshot: Any?): ExecutionResult {
        val snap = snapshot as? Snapshot
        val executionRequest = request as? ExecutionRequest
        val offset = executionRequest?.getSingle(JobConventions.previewOffsetParameter)
            ?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val limit = executionRequest?.getSingle(JobConventions.previewLimitParameter)
            ?.toIntOrNull()?.coerceAtLeast(0) ?: sample

        val rows = snap?.items ?: listOf()
        val slice =
            if (offset >= rows.size) {
                listOf()
            }
            else {
                rows.subList(offset, offset + minOf(rows.size - offset, limit)).toList()
            }

        return ExecutionSuccess.ofValue(ExecutionValue.of(
            wire(slice, snap?.count ?: 0L) +
                    (JobConventions.previewOffsetParameter to offset.toLong())))
    }

    class Snapshot(val items: List<PreviewNode>, val count: Long) {
        val header: List<String> get() = PreviewTable(items).columns.map { it.label }
        val rows: List<List<String>> get() = PreviewTable(items).rows.map { row -> row.map { it.text } }
    }

    // Stands in for an output nothing consumes: nothing is ever sent to it
    private object Unconsumed: ChannelOutput<DataValue> {
        override suspend fun send(element: DataValue) {
            error("A Preview whose output is not consumed forwards nothing")
        }
        override suspend fun flush() {}
        override fun batchSize(): Int = 1
        override fun close() {}
    }
}
