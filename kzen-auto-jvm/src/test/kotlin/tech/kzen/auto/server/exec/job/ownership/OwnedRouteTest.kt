package tech.kzen.auto.server.exec.job.ownership

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.exec.job.JobLogicCompiler
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.server.exec.engine.RunEngine
import java.util.concurrent.Semaphore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue


/**
 * E9 through the transport and the framework drive loops (HS16): a Worker-created closeable is adopted at
 * send, every channel hop and callback holds it, and it closes exactly once after the final holder — on the
 * linear route, past a scalar projection, across a fan-out, through a Sort's retention, for a derived
 * closeable and its parent, on a failing run, and with an arena-backed source at channel capacities 0, 1 and 4.
 */
class OwnedRouteTest {
    private val runTimeoutMillis = 30_000L

    private lateinit var context: KzenAutoContext


    @BeforeTest
    fun reset() {
        OwnedSourceWorker.reset()
        ForwardingTransformWorker.reset()
        ObservingSinkWorker.reset()
        CloseCountingResource.reset()
    }

    @AfterTest
    fun tearDown() {
        if (::context.isInitialized) {
            context.close()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun linearRouteClosesEachElementOnceAfterTheSinkIsDoneWithIt() {
        assertIs<Outcome.Success>(run("linear"))
        val observed = ObservingSinkWorker.of("sink")
        assertEquals(listOf<Any?>("c", "b", "a"), observed.map { it.value })
        assertTrue(observed.all { it.openAtReceipt == true }, "the sink sees every element open")
        assertTrue(ForwardingTransformWorker.seenOpen.all { it })
        assertClosedOnce(OwnedSourceWorker.resources)
    }


    @Test
    fun projectionToScalarClosesTheElementWhenTheProjectingCallbackReturns() {
        assertIs<Outcome.Success>(run("scalar"))
        val observed = ObservingSinkWorker.of("sink")
        assertEquals(listOf<Any?>("c", "b", "a"), observed.map { it.value })
        // A scalar carries no owner: the transform's callback was the last holder, so by the time the unowned
        // scalars reach the sink (flushed at the end of the input batch) their sources are closed
        assertTrue(observed.all { it.openAtReceipt == null })
        assertClosedOnce(OwnedSourceWorker.resources)
        assertTrue(OwnedSourceWorker.resources.all { it.closeThread != null })
    }


    @Test
    fun fanOutHoldsOncePerChannelAndClosesAfterBothConsumers() {
        assertIs<Outcome.Success>(run("fanout"))
        val left = ObservingSinkWorker.of("left")
        val right = ObservingSinkWorker.of("right")
        assertEquals(listOf<Any?>("c", "b", "a"), left.map { it.value })
        assertEquals(listOf<Any?>("c", "b", "a"), right.map { it.value })
        assertTrue((left + right).all { it.openAtReceipt == true }, "neither consumer sees a closed element")
        assertClosedOnce(OwnedSourceWorker.resources)
    }


    @Test
    fun sortRetainsUntilItsOwnEmissionAndTheSinkStillSeesEveryElementOpen() {
        assertIs<Outcome.Success>(run("sort"))
        val observed = ObservingSinkWorker.of("sink")
        assertEquals(listOf<Any?>("a", "b", "c"), observed.map { it.value }, "sorted by name")
        assertTrue(observed.all { it.openAtReceipt == true }, "the Sort's holds outlived the source")
        assertClosedOnce(OwnedSourceWorker.resources)
    }


    @Test
    fun derivedCloseableClosesWithItsConsumerAndTheParentOnlyAfterIt() {
        assertIs<Outcome.Success>(run("derived"))
        val observed = ObservingSinkWorker.of("sink")
        assertEquals(listOf<Any?>("derived-c", "derived-b", "derived-a"), observed.map { it.value })
        assertTrue(observed.all { it.openAtReceipt == true })
        assertClosedOnce(OwnedSourceWorker.resources)
        assertClosedOnce(ForwardingTransformWorker.derived)
        // The sink's release let go of the child's own entry and the inherited parent together: child first
        val order = CloseCountingResource.closeOrder
        for (name in listOf("a", "b", "c")) {
            assertTrue(order.indexOf("derived-$name") < order.indexOf(name), order.toString())
        }
    }


    @Test
    fun failingTransformKeepsItsFailurePrimaryAndEverythingStillCloses() {
        ForwardingTransformWorker.failAtIndex = 1
        val outcome = run("failing")
        val failed = assertIs<Outcome.Failed>(outcome)
        assertTrue(failed.toString().contains("transform failed at element 1"), failed.toString())
        assertClosedOnce(OwnedSourceWorker.resources)
    }


    @Test
    fun ownedElementsFlushAtOnceSoAnArenaBackedSourceNeverStallsBehindItsBatch() {
        for (job in listOf("permit0", "permit1", "permit4")) {
            tearDown()
            reset()
            OwnedSourceWorker.names = listOf("p1", "p2", "p3", "p4")
            OwnedSourceWorker.permits = Semaphore(1)
            val outcome = run(job)
            assertIs<Outcome.Success>(outcome, "$job: $outcome")
            val observed = ObservingSinkWorker.of("sink")
            assertEquals(listOf<Any?>("p1", "p2", "p3", "p4"), observed.map { it.value }, job)
            assertTrue(observed.all { it.openAtReceipt == true }, job)
            assertClosedOnce(OwnedSourceWorker.resources)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun assertClosedOnce(resources: List<CloseCountingResource>) {
        assertTrue(resources.isNotEmpty())
        assertTrue(resources.all { it.closeCount.get() == 1 }, resources.toString())
    }


    private fun run(job: String): Outcome {
        val engine = newEngine(job)
        return try {
            runBlocking {
                withTimeout(runTimeoutMillis) {
                    engine.resume()
                    engine.await()
                }
            }
        }
        finally {
            engine.close()
        }
    }


    private fun newEngine(job: String): RunEngine {
        context = KzenAutoContext.forTest()
        val jobLocation = ObjectLocation(
            DocumentPath.parse("test/job/ownership/owned-route-$job.yaml"), ObjectPath.parse("main"))
        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinition = AutoTestUtils.graphDefinitionAttempt(graphNotation).transitiveSuccessful
        val jobLogic = JobLogicCompiler.compile(
            jobLocation,
            graphNotation,
            graphDefinition,
            LogicCompilerServices(
                context.graphEnvironment,
                context.objectStableMapper,
                context.cachedKotlinCompiler,
                context.scriptValidationCache,
                context.jobValidationCache,
                context.notationMetadataReader,
                context.jobWorkPool,
                LogicRunExecutionId.random()))
        return RunEngine(jobLogic, context.objectStableMapper.objectStableId(jobLocation))
    }
}
