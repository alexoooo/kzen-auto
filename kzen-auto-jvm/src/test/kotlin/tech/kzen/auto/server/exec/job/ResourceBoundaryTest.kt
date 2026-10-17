package tech.kzen.auto.server.exec.job

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.objects.job.worker.test.CloseCountingResource
import tech.kzen.auto.server.objects.job.worker.test.ForwardingTransformWorker
import tech.kzen.auto.server.objects.job.worker.test.GatedCountingSinkWorker
import tech.kzen.auto.server.objects.job.worker.test.ObservingSinkWorker
import tech.kzen.auto.server.objects.job.worker.test.OpaqueHandle
import tech.kzen.auto.server.objects.job.worker.test.OwnedRecord
import tech.kzen.auto.server.objects.job.worker.test.OwnedSourceWorker
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.auto.server.util.hangGuardMillis
import tech.kzen.lib.common.exec.data.binding.BindingName
import tech.kzen.lib.common.exec.data.binding.BindingState
import tech.kzen.lib.common.exec.data.value.DataAccessException
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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue


/**
 * The E9 boundary, diagnostics and acceptance gate (HS18, RH2): a Result over an owned element is the live
 * value — readable by the frame that hosts the Job, closed once the frame it was returned to settles, and a
 * root's result closes at the root's own settle; an opaque owned native passes through a Result the same way;
 * a Borrowed child is never closed while its parent still is; a Sort's explicit holds survive a live edit;
 * a source parked behind a Sort waits without failing and a cancel closes what it holds, while a copied
 * projection before the Sort (independent outputs) keeps the same source flowing on one permit; and the failure
 * paths of the earlier sessions still close exactly once.
 */
class ResourceBoundaryTest {
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
    fun previewReleasesNestedOwnedValuesAndKeepsDetachedContent() {
        OwnedSourceWorker.kind = OwnedSourceWorker.kindOrder
        val engine = newEngine(document("preview"))
        try {
            engine.resume()
            assertIs<Outcome.Success>(runBlocking { withTimeout(hangGuardMillis) { engine.await() } })
            assertTrue(OwnedSourceWorker.orders.all { it.closes == 1 })
            val progress = engine.snapshot().root.children.mapNotNull {
                it.live[Address.of("\$job-progress")]?.get() as? Map<*, *>
            }.first { it.containsKey("previewItems") }
            val items = progress["previewItems"] as List<*>
            assertEquals(3, items.size)
            val first = tech.kzen.auto.common.objects.document.job.preview.PreviewNode.decode(items.first() as String)
            val executions = first.children.single { it.name == "executions" }
            assertEquals(2, executions.children.size)
            assertEquals("10.0", executions.children.first().children.single { it.name == "price" }.text)
        }
        finally { engine.close() }
    }

    @Test
    fun formulaPreviewReleasesNestedOwnedValuesAndKeepsCalculatedContent() {
        OwnedSourceWorker.kind = OwnedSourceWorker.kindOrder
        val engine = newEngine(document("formula-preview"))
        try {
            engine.resume()
            assertIs<Outcome.Success>(runBlocking { withTimeout(hangGuardMillis) { engine.await() } })
            assertTrue(OwnedSourceWorker.orders.all { it.closes == 1 })
            val progress = engine.snapshot().root.children.mapNotNull {
                it.live[Address.of("\$job-progress")]?.get() as? Map<*, *>
            }.first { it.containsKey("previewItems") }
            val items = progress["previewItems"] as List<*>
            assertEquals(3, items.size)
            val first = tech.kzen.auto.common.objects.document.job.preview.PreviewNode.decode(items.first() as String)
            val metadata = checkNotNull(first.metadata) { first.encode() }
            assertEquals(OwnedSourceWorker.orders.first().symbol.length.toString(), metadata.children.single { it.name == "test" }.text)
            val executions = first.children.single { it.name == "executions" }
            assertEquals(2, executions.children.size)
            assertEquals("10.0", executions.children.first().children.single { it.name == "price" }.text)
        }
        finally { engine.close() }
    }

    @Test
    fun cancellingFormulaWithAQueuedWrapperReleasesItsNative() {
        OwnedSourceWorker.kind = OwnedSourceWorker.kindOrder
        val engine = newEngine(document("formula-cancel"))
        try {
            engine.resume()
            awaitCondition("Formula output queued") {
                engine.snapshot().root.children.any {
                    val progress = it.live[Address.of("\$job-progress")]?.get() as? Map<*, *>
                    ((progress?.get("computed") as? Number)?.toLong() ?: 0L) >= 1L
                }
            }
            assertTrue(OwnedSourceWorker.orders.any { it.closes == 0 })
            engine.cancel()
            assertIs<Outcome.Cancelled>(runBlocking { withTimeout(hangGuardMillis) { engine.await() } })
        }
        finally { engine.close() }
        assertTrue(OwnedSourceWorker.orders.isNotEmpty())
        assertTrue(
            OwnedSourceWorker.orders.all { it.closes == 1 },
            OwnedSourceWorker.orders.map { it.symbol to it.closes }.toString())
    }

    @Test
    fun failingAfterFormulaReleasesItsWrappedNative() {
        OwnedSourceWorker.kind = OwnedSourceWorker.kindOrder
        val outcome = run(document("formula-failure"))
        assertIs<Outcome.Failed>(outcome)
        assertTrue(outcome.toString().contains("formula fixture failure"), outcome.toString())
        assertTrue(OwnedSourceWorker.orders.isNotEmpty())
        assertTrue(OwnedSourceWorker.orders.all { it.closes == 1 })
    }

    @Test
    fun aRootResultIsTheLiveOwnedValueClosedOnceAtTheRootsSettle() {
        OwnedSourceWorker.kind = OwnedSourceWorker.kindRecord
        val success = assertIs<Outcome.Success>(run(document("result")))
        val bound = assertIs<BindingState.Bound>(success.value[BindingName("main")])
        val kept = OwnedSourceWorker.records.last()
        assertSame(kept, bound.value.access.native(bound.value.root), "the live record, never a copy")
        assertEquals("a", kept.name)
        assertEquals(3, OwnedSourceWorker.records.size)
        assertTrue(OwnedSourceWorker.records.all { it.closes == 1 }, OwnedSourceWorker.records.toString())
        val failure = assertFailsWith<DataAccessException> { JobDataValues.boundary(bound.value) }
        assertTrue(failure.message!!.contains("closed"), failure.message)
    }


    @Test
    fun aHostedResultIsReadableLiveInTheHostingFrameAndClosesWhenThatFrameSettles() {
        assertIs<Outcome.Success>(run(document("hosted-result")))
        val observed = ObservingSinkWorker.of("sink").single()
        assertEquals("a", observed.value, "the child's kept element reaches the host's sink")
        assertEquals(true, observed.openAtReceipt, "open in the hosting frame")
        assertEquals(3, OwnedSourceWorker.resources.size)
        assertTrue(OwnedSourceWorker.resources.all { it.closeCount.get() == 1 }, OwnedSourceWorker.resources.toString())
    }


    @Test
    fun keepAllReturnsEveryOwnedElementLive() {
        OwnedSourceWorker.kind = OwnedSourceWorker.kindRecord
        val success = assertIs<Outcome.Success>(run(document("all")))
        val bound = assertIs<BindingState.Bound>(success.value[BindingName("main")])
        val all = assertIs<List<*>>(bound.value.access.native(bound.value.root))
        assertEquals(OwnedSourceWorker.records.toList(), all)
        assertEquals(listOf("c", "b", "a"), all.map { (it as OwnedRecord).name })
        assertTrue(OwnedSourceWorker.records.all { it.closes == 1 })
    }


    @Test
    fun anOpaqueOwnedNativeAtAResultPassesThroughLiveAndClosesAtSettle() {
        OwnedSourceWorker.kind = OwnedSourceWorker.kindOpaque
        val success = assertIs<Outcome.Success>(run(document("opaque")))
        val bound = assertIs<BindingState.Bound>(success.value[BindingName("main")])
        assertSame(OwnedSourceWorker.opaques.last(), bound.value.access.native(bound.value.root))
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
    fun sortsExplicitHoldsSurviveALiveEditAndCloseAfterTheReplacementCompletes() {
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
            val outcome = runBlocking { withTimeout(hangGuardMillis) { engine.await() } }
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
    fun aSourceParkedBehindASortWaitsWithoutFailingAndCancelClosesWhatItHolds() {
        OwnedSourceWorker.names = listOf("d", "c", "b", "a")
        OwnedSourceWorker.permits = Semaphore(2)
        val engine = newEngine(document("stall"))
        try {
            engine.resume()
            awaitCondition("two items adopted") { OwnedSourceWorker.resources.size == 2 }
            assertFalse(engine.snapshot().root.status.toString().contains("Failed"), "waiting is not a failure")
            engine.cancel()
            assertIs<Outcome.Cancelled>(runBlocking { withTimeout(hangGuardMillis) { engine.await() } })
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
    private fun awaitCondition(what: String, timeoutMillis: Long = hangGuardMillis, condition: () -> Boolean) {
        val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (!condition()) {
            assertTrue(System.nanoTime() < deadlineNanos, "timed out waiting for $what")
            Thread.sleep(10)
        }
    }


    private fun document(route: String): DocumentPath =
        DocumentPath.parse("test/job/ownership/owned-boundary-$route.yaml")


    private fun run(documentPath: DocumentPath): Outcome {
        val engine = newEngine(documentPath)
        return try {
            runBlocking {
                withTimeout(hangGuardMillis) {
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
