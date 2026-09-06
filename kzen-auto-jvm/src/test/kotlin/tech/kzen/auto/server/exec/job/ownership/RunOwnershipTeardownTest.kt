package tech.kzen.auto.server.exec.job.ownership

import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.exec.job.JobLogicCompiler
import tech.kzen.auto.server.objects.job.worker.javafixture.CollectingSinkWorker
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.server.exec.engine.RunEngine
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue


/**
 * The run's post-join teardown seam (E9 item 2, HS15): everything the ledger owns is closed after the Workers
 * have joined — on completion, on cancellation, and on failure, where the processing failure stays the run's
 * outcome and close failures ride along suppressed.
 */
class RunOwnershipTeardownTest {
    private val documentPath = DocumentPath.parse("test/job/ownership/adopting-source-test.yaml")
    private val jobLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))
    private val latchTimeoutSeconds = 10L

    private lateinit var context: KzenAutoContext


    @BeforeTest
    fun reset() {
        AdoptingSourceWorker.reset()
        CollectingSinkWorker.reset()
        CloseCountingResource.reset()
    }

    @AfterTest
    fun tearDown() {
        if (::context.isInitialized) {
            context.close()
        }
    }


    @Test
    fun completedRunClosesEveryAdoptedResourceExactlyOnceAfterTheWorkersJoined() {
        AdoptingSourceWorker.count = 3
        val outcome = run()
        assertIs<Outcome.Success>(outcome)
        assertEquals(listOf<Any?>(0, 1, 2), CollectingSinkWorker.received.toList())
        assertEquals(3, AdoptingSourceWorker.resources.size)
        assertTrue(AdoptingSourceWorker.resources.all { it.closeCount.get() == 1 }, AdoptingSourceWorker.resources.toString())
    }


    @Test
    fun cancelledRunClosesNothingUntilTheSourceHasJoinedThenClosesEverything() {
        val adopted = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        AdoptingSourceWorker.adopted = adopted
        AdoptingSourceWorker.proceed = proceed

        val engine = newEngine()
        val outcome = try {
            runBlocking {
                engine.resume()
                assertTrue(adopted.await(latchTimeoutSeconds, TimeUnit.SECONDS))
                engine.cancel()
                // The source is parked in a blocking wait: nothing may close while it could still be using the resource
                Thread.sleep(200)
                assertTrue(AdoptingSourceWorker.resources.none { it.isClosed }, "no close under a live blocking call")
                proceed.countDown()
                engine.await()
            }
        }
        finally {
            engine.close()
        }
        assertIs<Outcome.Cancelled>(outcome)
        assertTrue(AdoptingSourceWorker.resources.all { it.closeCount.get() == 1 })
    }


    @Test
    fun processingFailureStaysPrimaryAndCloseFailuresAreSuppressed() {
        AdoptingSourceWorker.failAfterAdopting = true
        AdoptingSourceWorker.throwOnClose = true
        val outcome = run()
        val failed = assertIs<Outcome.Failed>(outcome)
        assertTrue(failed.toString().contains("source failed after adopting"), failed.toString())
        assertTrue(AdoptingSourceWorker.resources.all { it.closeCount.get() == 1 }, "every close was attempted once")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun run(): Outcome {
        val engine = newEngine()
        return try {
            runBlocking {
                engine.resume()
                engine.await()
            }
        }
        finally {
            engine.close()
        }
    }


    private fun newEngine(): RunEngine {
        context = KzenAutoContext.forTest()
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
