package tech.kzen.auto.server.objects.job.worker

import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelInputIterator
import tech.kzen.auto.common.paradigm.job.api.ChannelServer
import tech.kzen.auto.common.paradigm.job.api.ChannelServerIterator
import tech.kzen.auto.common.paradigm.job.api.JobLogicHost
import tech.kzen.auto.common.paradigm.job.api.ServedRequest
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import kotlin.test.assertEquals


/**
 * Unit test for [PreviewWorker]'s SCALAR lane in isolation: drives the sink's full [PreviewWorker.run] lifecycle
 * over a fake [ChannelInput] of arbitrary (non-[RecordBatch]) elements — the shape a FormulaSource / Run
 * pipeline emits — and asserts each renders as a single `value` column. The RecordBatch lane and the duplex
 * serve path are already covered end-to-end by [tech.kzen.auto.server.objects.job.JobExecutionTest]; this
 * isolates the new branch added so one Preview view serves both lanes.
 */
class PreviewWorkerTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun scalarElementsRenderAsSingleValueColumn() = runBlocking {
        val snapshot = runPreview(listOf("1", "2", "Fizz", 4, "Buzz"))
        assertEquals(listOf("value"), snapshot.header)
        assertEquals(5L, snapshot.count)
        assertEquals(
            listOf(listOf("1"), listOf("2"), listOf("Fizz"), listOf("4"), listOf("Buzz")),
            snapshot.rows)
    }


    @Test
    fun nullScalarRendersAsEmptyString() = runBlocking {
        val snapshot = runPreview(listOf(null, "x"))
        assertEquals(listOf("value"), snapshot.header)
        assertEquals(listOf(listOf(""), listOf("x")), snapshot.rows)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun runPreview(elements: List<Any?>): PreviewWorker.Snapshot {
        val selfLocation = ObjectLocation(
            DocumentPath.parse("test/preview-unit-test.yaml"),
            ObjectPath.parse("main.workers/preview"))

        val worker = PreviewWorker(scalarInput(elements), emptyServer, 1000, selfLocation)
        worker.run(NoOpJobControl)

        // captureMigrationState() returns the same immutable snapshot() the trace / serve paths read.
        return worker.captureMigrationState() as PreviewWorker.Snapshot
    }


    private fun scalarInput(elements: List<Any?>): ChannelInput<Any?> =
        object: ChannelInput<Any?> {
            override suspend fun receive(): Any? = error("unused")

            override fun iterator(): ChannelInputIterator<Any?> =
                object: ChannelInputIterator<Any?> {
                    private var index = 0
                    override suspend fun hasNext(): Boolean = index < elements.size
                    override fun next(): Any? = elements[index++]
                }
        }


    // A serve channel that ends immediately, so the framework's serve loop exits without serving any request.
    private val emptyServer = object: ChannelServer<Any?, Any?> {
        override suspend fun receive(): ServedRequest<Any?, Any?>? = null

        override fun iterator(): ChannelServerIterator<Any?, Any?> =
            object: ChannelServerIterator<Any?, Any?> {
                override suspend fun hasNext(): Boolean = false
                override fun next(): ServedRequest<Any?, Any?> = error("no requests")
            }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // A sink Preview only consumes + checkpoints + publishes; none of those need coordination in isolation.
    private object NoOpJobControl: JobControl {
        override suspend fun checkpoint() {}
        override suspend fun <R> runBlockingIo(block: () -> R): R = block()
        override fun publishProgress(location: ObjectLocation, value: Map<String, Any?>, force: Boolean) {}
        override fun logicHost(): JobLogicHost = error("nested logic not used by PreviewWorker")
    }
}
