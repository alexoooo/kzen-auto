package tech.kzen.auto.server.exec.job.ownership

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.exec.job.JobDeadlockMonitor
import tech.kzen.auto.server.exec.job.JobLogic
import tech.kzen.auto.server.exec.job.JobLogicCompiler
import tech.kzen.auto.server.exec.job.JobOwnershipReport
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.objects.job.worker.test.GatedCountingSinkWorker
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.data.binding.BindingName
import tech.kzen.lib.common.exec.data.binding.BindingState
import tech.kzen.lib.common.exec.data.binding.DataBindings
import tech.kzen.lib.common.exec.engine.Address
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
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue


/**
 * The E9 boundary, diagnostics and acceptance gate (HS18): a Result keeps a structural snapshot of an owned
 * element and stays readable after the run closed it; an opaque owned native fails by name at the boundary;
 * a Borrowed child is never closed while its parent still is; a Sort's explicit leases survive a live edit;
 * a source stalled behind a Sort produces the delayed, named, non-failing warning while a copied projection
 * before the Sort (independent outputs) keeps the same source flowing on one permit; and the failure paths of
 * the earlier sessions still close exactly once.
 */
class OwnershipBoundaryTest {
    private val runTimeoutMillis = 30_000L
    private val latchTimeoutSeconds = 10L

    private lateinit var context: KzenAutoContext


    @BeforeTest
    fun reset() {
        OwnedSourceWorker.reset()
        ForwardingTransformWorker.reset()
        ObservingSinkWorker.reset()
        GatedCountingSinkWorker.reset()
        CloseCountingResource.reset()
        OpaqueHandle.closes.set(0)
    }

    @AfterTest
    fun tearDown() {
        if (::context.isInitialized) {
            context.close()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun resultKeepsASnapshotThatStaysReadableAfterTheRunClosedTheNative() {
        OwnedSourceWorker.kind = OwnedSourceWorker.kindRecord
        val success = assertIs<Outcome.Success>(run(document("result")))
        val result = mainResult(success.value)
        val record = assertIs<Map<*, *>>(result, "a structural copy, never the native: $result")
        assertEquals("a", record["name"])
        assertEquals(2, (record["value"] as Number).toInt())
        assertEquals(0, (record["closes"] as Number).toInt(), "snapshotted while open")
        assertEquals(3, OwnedSourceWorker.records.size)
        assertTrue(OwnedSourceWorker.records.all { it.closes == 1 }, OwnedSourceWorker.records.toString())
    }


    @Test
    fun keepAllSnapshotsEveryOwnedElement() {
        OwnedSourceWorker.kind = OwnedSourceWorker.kindRecord
        val success = assertIs<Outcome.Success>(run(document("all")))
        val all = assertIs<List<*>>(mainResult(success.value))
        assertEquals(listOf("c", "b", "a"), all.map { (it as Map<*, *>)["name"] })
        assertTrue(all.none { it is AutoCloseable })
        assertTrue(OwnedSourceWorker.records.all { it.closes == 1 })
    }


    @Test
    fun anOpaqueOwnedNativeAtAResultFailsByNameAndStillCloses() {
        OwnedSourceWorker.kind = OwnedSourceWorker.kindOpaque
        val failed = assertIs<Outcome.Failed>(run(document("opaque")))
        val message = failed.toString()
        assertTrue(message.contains("cannot leave the run") && message.contains("OpaqueHandle"), message)
        assertEquals(OwnedSourceWorker.opaques.size, OpaqueHandle.closes.get(), "every adopted handle closed once")
        assertTrue(OwnedSourceWorker.opaques.isNotEmpty())
    }


    @Test
    fun borrowedChildIsNeverClosedAndItsParentClosesAfterTheConsumer() {
        assertIs<Outcome.Success>(run(document("borrowed")))
        val observed = ObservingSinkWorker.of("sink")
        assertEquals(listOf<Any?>("borrowed-c", "borrowed-b", "borrowed-a"), observed.map { it.value })
        assertTrue(observed.all { it.openAtReceipt == true })
        assertTrue(ForwardingTransformWorker.derived.all { it.closeCount.get() == 0 }, "kzen never closes a borrowed child")
        assertTrue(OwnedSourceWorker.resources.all { it.closeCount.get() == 1 }, "the parent closes once, after the sink")
    }


    @Test
    fun sortsExplicitLeasesSurviveALiveEditAndCloseAfterTheReplacementCompletes() {
        OwnedSourceWorker.names = listOf("f", "e", "d", "c", "b", "a")
        context = KzenAutoContext.forTest()
        val documentPath = document("sort-migration")
        val jobLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))
        val sinkLocation = ObjectLocation(documentPath, ObjectPath.parse("main.workers/sink"))
        val notation = AutoTestUtils.readNotation()
        val baseLogic = compile(jobLocation, notation)
        val editedLogic = compile(jobLocation, edit(notation, sinkLocation, "note", "edited"))
        val engine = RunEngine(baseLogic, context.objectStableMapper.objectStableId(jobLocation))
        try {
            engine.resume()
            // The Sort buffers everything, then parks sending into the gated sink's channel
            awaitCondition("the Sort buffered the whole stream") { OwnedSourceWorker.resources.size == 6 }
            Thread.sleep(300)
            engine.pause()
            engine.awaitQuiescent()
            assertTrue(OwnedSourceWorker.resources.none { it.isClosed }, "held by the Sort / the channel across the cut")
            engine.migrate(editedLogic, paused = false)
            val outcome = runBlocking { withTimeout(runTimeoutMillis) { engine.await() } }
            assertIs<Outcome.Success>(outcome)
            val sortProgress = engine.snapshot().root.children
                .map { it.stableId.value + ": " + it.status + " " + it.live[Address.of("\$job-progress")]?.get() }
            assertEquals(6L, GatedCountingSinkWorker.received.get(), "every element once; workers: $sortProgress")
        }
        finally {
            engine.close()
        }
        assertTrue(OwnedSourceWorker.resources.all { it.closeCount.get() == 1 }, OwnedSourceWorker.resources.toString())
    }


    @Test
    fun aSourceStalledBehindASortWarnsNamingTheSortOnlyAfterTheInterval() {
        OwnedSourceWorker.names = listOf("d", "c", "b", "a")
        OwnedSourceWorker.permits = Semaphore(2)
        val engine = newEngine(document("stall"))
        try {
            engine.resume()
            awaitCondition("two items adopted") { OwnedSourceWorker.resources.size == 2 }
            // Not from the state combination alone: no report before the interval
            Thread.sleep(JobDeadlockMonitor.stallIntervalMillis / 4)
            assertNull(ownershipReport(engine), "nothing reported before the no-progress interval")
            awaitCondition("the stall warning", JobDeadlockMonitor.stallIntervalMillis * 2) {
                ownershipReport(engine)?.get(JobConventions.ownershipStalledKey) == true
            }
            val report = ownershipReport(engine)!!
            val holds = assertIs<Map<*, *>>(report[JobConventions.ownershipHoldsKey])
            val sortHolder = holds.keys.map { it.toString() }.firstOrNull { it.endsWith("main.workers/sort") }
            assertTrue(sortHolder != null, "the Sort is named as a holder: $holds")
            assertEquals(2L, (holds[sortHolder] as Number).toLong())
            assertFalse(engine.snapshot().root.status.toString().contains("Failed"), "a warning, not a verdict")
            engine.cancel()
            assertIs<Outcome.Cancelled>(runBlocking { withTimeout(runTimeoutMillis) { engine.await() } })
        }
        finally {
            engine.close()
        }
        assertTrue(OwnedSourceWorker.resources.all { it.closeCount.get() == 1 })
    }


    @Test
    fun aCopiedProjectionBeforeASortRetainsRowsNotNativesAndCompletesOnOnePermit() {
        // The plain-Java analytical shape: a JavaTransformWorker reads a row of scalars off each owned element and
        // declares the rows independent copies. A one-permit arena then never blocks — each element closes when
        // the projecting callback returns — and the Sort retains rows only; under the default (inheriting) rule
        // the same route parks the source on its second permit behind the Sort, as the "stall" route shows.
        OwnedSourceWorker.names = listOf("d", "c", "b", "a")
        OwnedSourceWorker.permits = Semaphore(1)
        assertIs<Outcome.Success>(run(document("copied")))
        val observed = ObservingSinkWorker.of("sink")
        assertEquals(listOf("a", "b", "c", "d"), observed.map { (it.value as Map<*, *>)["name"] })
        assertTrue(observed.all { it.openAtReceipt == null }, "rows, not natives, reach the sink")
        assertTrue(OwnedSourceWorker.resources.all { it.closeCount.get() == 1 }, OwnedSourceWorker.resources.toString())
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ownershipReport(engine: RunEngine): Map<*, *>? =
        engine.snapshot().root.live[Address.of(JobOwnershipReport.addressMarker)]?.get() as? Map<*, *>


    private fun awaitCondition(what: String, timeoutMillis: Long = latchTimeoutSeconds * 1000, condition: () -> Boolean) {
        val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (!condition()) {
            assertTrue(System.nanoTime() < deadlineNanos, "timed out waiting for $what")
            Thread.sleep(10)
        }
    }


    private fun mainResult(bindings: DataBindings): Any? {
        val bound = assertIs<BindingState.Bound>(bindings[BindingName("main")])
        return JobDataValues.boundary(bound.value)
    }


    private fun document(route: String): DocumentPath =
        DocumentPath.parse("test/job/ownership/owned-boundary-$route.yaml")


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
