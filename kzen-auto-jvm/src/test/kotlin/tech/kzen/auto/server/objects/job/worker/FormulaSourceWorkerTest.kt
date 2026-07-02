package tech.kzen.auto.server.objects.job.worker

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import kotlin.test.assertEquals


/**
 * Unit test for [FormulaSourceWorker] in isolation: drives the Worker's full [FormulaSourceWorker.run]
 * lifecycle (compile the Kotlin expression -> evaluate to an Iterable -> emit each element) against a capturing
 * [ChannelOutput] and a no-op [JobControl], using the real [CachedKotlinCompiler] from a test context. The
 * archetype wiring (JobChannelCreator handing the Worker a real channel view) is already covered by
 * [tech.kzen.auto.server.objects.job.JobExecutionTest] for the other Workers; this isolates the new Worker's
 * own compile-and-iterate logic.
 */
class FormulaSourceWorkerTest {
    //-----------------------------------------------------------------------------------------------------------------
    private lateinit var context: KzenAutoContext


    @Before
    fun setUp() {
        context = KzenAutoContext.forTest()
    }


    @After
    fun tearDown() {
        context.close()
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun evaluatesIterableExpressionAndEmitsEachElement() = runBlocking {
        val emitted = runSource("(1..5).map { \"row\" + it }")
        assertEquals(listOf("row1", "row2", "row3", "row4", "row5"), emitted)
    }


    @Test
    fun emptyIterableEmitsNothing() = runBlocking {
        val emitted = runSource("listOf<String>()")
        assertEquals(listOf(), emitted)
    }


    @Test
    fun rangeIterableEmitsEachInteger() = runBlocking {
        val emitted = runSource("1..3")
        assertEquals(listOf(1, 2, 3), emitted)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun runSource(code: String): List<Any?> {
        val emitted = mutableListOf<Any?>()
        val output = object: ChannelOutput<Any?> {
            override suspend fun send(element: Any?) {
                emitted.add(element)
            }
            override suspend fun flush() {}
            override fun chunkSize(): Int = 1024
            override fun close() {}
        }

        val selfLocation = ObjectLocation(
            DocumentPath.parse("test/formula-source-unit-test.yaml"),
            ObjectPath.parse("main.workers/source"))

        val worker = FormulaSourceWorker(
            output, code, selfLocation, context.cachedKotlinCompiler)

        worker.run(NoOpJobControl)
        return emitted
    }


    //-----------------------------------------------------------------------------------------------------------------
    // A FormulaSourceWorker only emits + checkpoints + publishes; none of those need coordination in isolation.
    private object NoOpJobControl: JobControl {
        override suspend fun checkpoint() {}
        override suspend fun <R> runBlockingIo(block: () -> R): R = block()
        override fun scratchDir(): String =
            throw UnsupportedOperationException("A FormulaSourceWorker needs no scratch dir")
        override fun publishProgress(location: ObjectLocation, value: Map<String, Any?>, force: Boolean) {}
        override suspend fun host(instructions: ObjectLocation, input: Any?) =
            throw UnsupportedOperationException("A FormulaSourceWorker hosts no child")
    }
}
