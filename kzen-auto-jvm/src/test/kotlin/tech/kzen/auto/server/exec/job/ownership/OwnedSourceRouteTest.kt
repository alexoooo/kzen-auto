package tech.kzen.auto.server.exec.job.ownership

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.exec.job.JobLogic
import tech.kzen.auto.server.exec.job.JobLogicCompiler
import tech.kzen.auto.server.objects.job.worker.javafixture.CollectingSinkWorker
import tech.kzen.auto.server.objects.job.worker.javafixture.JavaCountingSource
import tech.kzen.auto.server.objects.job.worker.test.GatedCountingSinkWorker
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.service.notation.NotationReducer
import tech.kzen.lib.server.exec.engine.RunEngine
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue


/**
 * E9 at the source-ingress boundary (HS17): items and stream containers pulled by the framework are adopted
 * inside the blocking acquisition, held by the producer through lift and send, and closed by the run — on
 * the Java cursor route, the expression route (a `Stream`, a self-closing iterator, closeable elements), on a
 * lift failure, under a cancel that lands while the source waits for a permit and a sink callback is active,
 * and across a live-edit migration of a closeable stream (detached, never re-opened) and of a re-evaluated
 * list (delivered prefix skipped, its duplicates closed at once).
 */
class OwnedSourceRouteTest {
    private val runTimeoutMillis = 30_000L
    private val latchTimeoutSeconds = 10L
    private val migrationTotal = 40

    private lateinit var context: KzenAutoContext


    @BeforeTest
    fun reset() {
        ClosingStreams.reset()
        ContractMismatchSource.reset()
        ParkingSinkWorker.reset()
        OwnedSourceWorker.reset()
        ObservingSinkWorker.reset()
        JavaCountingSource.reset()
        CollectingSinkWorker.reset()
        GatedCountingSinkWorker.reset()
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
    fun javaCursorRouteClosesEveryPulledItemAndTheCursorOnce() {
        assertIs<Outcome.Success>(run(DocumentPath.parse("test/job/plugin/java-adapters-test.yaml")))
        assertEquals(listOf<Any?>(0, 0, 1, 2, 2, 4, "total=3"), CollectingSinkWorker.received.toList())
        assertEquals(3, JavaCountingSource.itemsCreated.get())
        assertEquals(3, JavaCountingSource.itemsClosed.get(), "each item closes after its consumer projected it")
        assertEquals(1, JavaCountingSource.cursorsClosed.get())
    }


    @Test
    fun expressionStreamOfCloseableElementsClosesElementsAfterTheSinkAndTheStreamOnce() {
        assertIs<Outcome.Success>(run(document("expression")))
        val observed = ObservingSinkWorker.of("sink")
        assertEquals(listOf<Any?>("r0", "r1", "r2"), observed.map { it.value })
        assertTrue(observed.all { it.openAtReceipt == true })
        assertEquals(3, ClosingStreams.elements.size)
        assertTrue(ClosingStreams.elements.all { it.closeCount.get() == 1 }, ClosingStreams.elements.toString())
        assertEquals(1, ClosingStreams.evaluations.get())
        assertEquals(1, ClosingStreams.streamCloses.get(), "the Stream container closes once")
    }


    @Test
    fun javaStreamOfScalarsIsAStreamLaneAndItsContainerClosesOnce() {
        assertIs<Outcome.Success>(run(document("scalars")))
        assertEquals(listOf<Any?>("s0", "s1", "s2"), ObservingSinkWorker.of("sink").map { it.value })
        assertEquals(1, ClosingStreams.streamCloses.get())
    }


    @Test
    fun iteratorThatIsItsOwnContainerClosesExactlyOnce() {
        assertIs<Outcome.Success>(run(document("selfclosing")))
        assertEquals(listOf<Any?>("i0", "i1", "i2"), ObservingSinkWorker.of("sink").map { it.value })
        assertEquals(1, ClosingStreams.streamCloses.get())
    }


    @Test
    fun liftFailureAfterThePullClosesTheItemAndIsTheRunsFailure() {
        val outcome = run(document("liftfailure"))
        val failed = assertIs<Outcome.Failed>(outcome)
        assertEquals(1, ContractMismatchSource.pulled.size, "the failing pull is the first")
        assertTrue(ContractMismatchSource.pulled.all { it.closeCount.get() == 1 }, ContractMismatchSource.pulled.toString())
        assertTrue(ObservingSinkWorker.observations.isEmpty())
        assertTrue(failed.toString().isNotBlank())
    }


    @Test
    fun cancelWhileTheSourceWaitsForAPermitAndACallbackIsActiveClosesNothingUntilTheCallbackReturns() {
        OwnedSourceWorker.names = listOf("a", "b", "c")
        OwnedSourceWorker.permits = Semaphore(1)
        val parked = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        ParkingSinkWorker.parked = parked
        ParkingSinkWorker.proceed = proceed

        val engine = newEngine(document("parked"))
        val outcome = try {
            runBlocking {
                engine.resume()
                assertTrue(parked.await(latchTimeoutSeconds, TimeUnit.SECONDS), "the sink parked in its callback")
                // The source is now waiting on the permit only "a"'s close would return: cancel lands while the
                // sink's callback is active and the source is inside its blocking pull
                engine.cancel()
                Thread.sleep(200)
                assertTrue(OwnedSourceWorker.resources.none { it.isClosed }, "nothing closes under an active callback")
                proceed.countDown()
                withTimeout(runTimeoutMillis) { engine.await() }
            }
        }
        finally {
            engine.close()
        }
        assertIs<Outcome.Cancelled>(outcome)
        assertEquals(true, ParkingSinkWorker.openWhileParked)
        assertTrue(OwnedSourceWorker.resources.isNotEmpty())
        assertTrue(OwnedSourceWorker.resources.all { it.closeCount.get() == 1 }, OwnedSourceWorker.resources.toString())
    }


    @Test
    fun migrationDetachesACloseableStreamWithoutReopeningAndDeliversEveryElementOnce() {
        migrateBehindGatedSink("stream-migration")
        assertEquals(1, ClosingStreams.evaluations.get(), "the closeable stream is never re-evaluated")
        assertEquals(1, ClosingStreams.streamCloses.get(), "closed once, by the replacement instance")
    }


    @Test
    fun migrationOfAReEvaluatedListSkipsTheDeliveredPrefixAndClosesItsDuplicates() {
        migrateBehindGatedSink("list-migration")
        assertEquals(2, ClosingStreams.evaluations.get(), "a non-closeable stream is re-evaluated and skipped")
        // Constructed as pulled: the prefix the first instance delivered, then the re-evaluation's full stream
        // (its skipped prefix included)
        val elements = ClosingStreams.elements
        assertTrue(elements.size in (migrationTotal + 1)..(2 * migrationTotal), elements.size.toString())
        val offenders = elements.filter { it.closeCount.get() != 1 }
        assertTrue(offenders.isEmpty(), "every constructed element — delivered or skipped — closed once; not: $offenders")
    }


    @Test
    fun aDetachedStreamOfARemovedSourceIsClosedWithItsPendingItems() {
        // The orphan path: what the engine does with the captured state of a Worker the edit removed
        val engine = newEngine(document("stream-migration"))
        try {
            runBlocking {
                engine.resume()
                awaitParkedSource()
                engine.pause()
                engine.awaitQuiescent()
                engine.cancel()
                assertIs<Outcome.Cancelled>(withTimeout(runTimeoutMillis) { engine.await() })
            }
        }
        finally {
            engine.close()
        }
        assertEquals(1, ClosingStreams.evaluations.get())
        assertEquals(1, ClosingStreams.streamCloses.get(), "cancel after the pause closes the open stream once")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun migrateBehindGatedSink(route: String) {
        context = KzenAutoContext.forTest()
        val documentPath = document(route)
        val jobLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))
        val sinkLocation = ObjectLocation(documentPath, ObjectPath.parse("main.workers/sink"))
        val notation = AutoTestUtils.readNotation()
        val baseLogic = compile(jobLocation, notation)
        val editedLogic = compile(jobLocation, edit(notation, sinkLocation, "note", "edited"))
        val engine = RunEngine(baseLogic, context.objectStableMapper.objectStableId(jobLocation))
        try {
            engine.resume()
            awaitParkedSource()
            engine.pause()
            engine.awaitQuiescent()
            assertEquals(0L, GatedCountingSinkWorker.received.get())
            engine.migrate(editedLogic, paused = false)
            val outcome = runBlocking { withTimeout(runTimeoutMillis) { engine.await() } }
            assertIs<Outcome.Success>(outcome)
            assertEquals(migrationTotal.toLong(), GatedCountingSinkWorker.received.get(), "every element once")
        }
        finally {
            engine.close()
        }
    }


    // The gated sink never drains: the source fills the buffer and parks mid-send — a stable state
    private fun awaitParkedSource() {
        val deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(latchTimeoutSeconds)
        while (ClosingStreams.evaluations.get() == 0) {
            assertTrue(System.nanoTime() < deadlineNanos, "the source never evaluated its stream")
            Thread.sleep(1)
        }
        // Buffer (batches of one) plus the parked send: give the source time to reach the stable state
        Thread.sleep(300)
    }


    private fun document(route: String): DocumentPath =
        DocumentPath.parse("test/job/ownership/owned-source-$route.yaml")


    private fun run(documentPath: DocumentPath): Outcome {
        val engine = newEngine(documentPath)
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


    private fun newEngine(documentPath: DocumentPath): RunEngine {
        context = KzenAutoContext.forTest()
        val jobLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))
        val jobLogic = compile(jobLocation, AutoTestUtils.readNotation())
        return RunEngine(jobLogic, context.objectStableMapper.objectStableId(jobLocation))
    }


    private fun compile(jobLocation: ObjectLocation, notation: GraphNotation): JobLogic {
        val definition = AutoTestUtils.graphDefinitionAttempt(notation).transitiveSuccessful
        return JobLogicCompiler.compile(
            jobLocation,
            notation,
            definition,
            LogicCompilerServices(
                context.graphEnvironment,
                context.objectStableMapper,
                context.cachedKotlinCompiler,
                context.scriptValidationCache,
                context.jobValidationCache,
                context.notationMetadataReader,
                context.jobWorkPool,
                LogicRunExecutionId.random()))
    }


    private fun edit(
        notation: GraphNotation,
        objectLocation: ObjectLocation,
        attribute: String,
        value: String
    ): GraphNotation =
        NotationReducer()
            .applyStructural(
                notation,
                UpsertAttributeCommand(
                    objectLocation, AttributeName(attribute), ScalarAttributeNotation(value)))
            .graphNotation
}
