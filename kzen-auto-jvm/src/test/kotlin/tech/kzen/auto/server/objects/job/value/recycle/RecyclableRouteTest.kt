package tech.kzen.auto.server.objects.job.value.recycle

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import tech.kzen.auto.common.objects.document.job.preview.PreviewNode
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.exec.job.JobLogic
import tech.kzen.auto.server.exec.job.JobLogicCompiler
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.objects.job.worker.PreviewWorker
import tech.kzen.auto.server.objects.job.worker.preview.PreviewCapture
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.auto.server.util.hangGuardMillis
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
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue


/**
 * Pooled values (WR2) through the Job's channels and Workers. A record goes back to its pool after its last
 * reader on the routes that only read it (Filter, a metadata-only or scalar Formula, Preview, an expanding
 * transform), and is forfeited — never recycled — wherever it may be kept (Sort, Result, a non-scalar Formula
 * payload, a child Logic, plain-Java code). Early close, failure mid-batch, cancellation and live-edit migration
 * with values in flight never recycle a record anything can still read; the pool stays at the in-flight count.
 *
 * Every record counts its returns to the pool in its generation, so the sum over every created record is the
 * number of recycles; a read of a recycled record fails by name and is counted.
 */
class RecyclableRouteTest {
    private val settleMillis = 300L

    private lateinit var context: KzenAutoContext


    @BeforeTest
    fun reset() {
        RecyclingSourceWorker.reset()
        RecyclableSinkWorker.reset()
        PooledRecord.useAfterRecycle.set(0)
    }

    @AfterTest
    fun tearDown() {
        PreviewWorker.captureNanos = PreviewCapture.defaultMaximumNanos
        if (::context.isInitialized) {
            context.close()
        }
        assertEquals(0, PooledRecord.useAfterRecycle.get(), "a record was read after it was recycled")
        assertEquals(0, RecyclableSinkWorker.ledgerOwned.get(), "a pooled record had ledger owners")
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun filterRecyclesKeptAndDroppedRecords() {
        assertIs<Outcome.Success>(run("filter"))
        assertEquals((0L until 100L step 2).toList(), sinkIds())
        assertAllRecycled()
    }


    @Test
    fun metadataOnlyFormulaSharesTheAccessAndKeepsTheRecordPooled() {
        assertIs<Outcome.Success>(run("formula-metadata"))
        assertEquals((0L until 50L).toList(), sinkIds())
        assertAllRecycled()
    }


    @Test
    fun scalarFormulaPayloadKeepsTheRecordPooled() {
        assertIs<Outcome.Success>(run("formula-scalar"))
        assertEquals((0 until 50).map { RecyclingSourceWorker.name(it, 50) }, RecyclableSinkWorker.of("sink"))
        assertAllRecycled()
    }


    @Test
    fun nonScalarFormulaPayloadForfeitsTheRecord() {
        assertIs<Outcome.Success>(run("formula-listing"))
        val received = RecyclableSinkWorker.of("sink")
        assertEquals((0 until 50).map { listOf(it.toLong(), RecyclingSourceWorker.name(it, 50)) }, received)
        assertAllForfeited()
    }


    @Test
    fun takeClosingEarlyRecyclesTheAbandonedRemainder() {
        assertIs<Outcome.Success>(run("take"))
        assertEquals((0L until 5L).toList(), sinkIds())
        // The Take's whole first input batch: five through the sink, the rest abandoned and released by the Take
        assertTrue(recycles() >= 1024, "recycled ${recycles()} of ${RecyclingSourceWorker.acquired.get()}")
        assertEquals(0, forfeited())
    }


    @Test
    fun sortRetainingForfeitsEveryRecordAndStillEmitsThemReadable() {
        assertIs<Outcome.Success>(run("sort"))
        assertEquals((19L downTo 0L).toList(), sinkIds(), "sorted by name, the reverse of the ids")
        assertAllForfeited()
    }


    @Test
    fun previewKeepsDetachedCopiesAndRecyclesEveryRecord() {
        PreviewWorker.captureNanos = Long.MAX_VALUE
        val engine = newEngine(document("preview"))
        try {
            engine.resume()
            assertIs<Outcome.Success>(runBlocking { withTimeout(hangGuardMillis) { engine.await() } })
            val items = progressOf(engine, "previewItems")["previewItems"] as List<*>
            assertEquals(5, items.size)
            val first = PreviewNode.decode(items.first() as String)
            assertEquals("0", first.children.single { it.name == "id" }.text)
            assertEquals(RecyclingSourceWorker.name(0, 5), first.children.single { it.name == "name" }.text)
        }
        finally {
            engine.close()
        }
        assertAllRecycled()
    }


    @Test
    fun resultKeepingTheFirstForfeitsOnlyThatRecord() {
        val result = assertIs<Map<*, *>>(mainResult(assertIs<Outcome.Success>(run("result-first")).value))
        assertEquals(0L, (result["id"] as Number).toLong())
        assertEquals(1, forfeited())
        assertEquals(4, recycles())
    }


    @Test
    fun resultKeepingTheLastForfeitsEveryRecordItSnapshotted() {
        val result = assertIs<Map<*, *>>(mainResult(assertIs<Outcome.Success>(run("result-last")).value))
        assertEquals(4L, (result["id"] as Number).toLong())
        assertAllForfeited()
    }


    @Test
    fun resultKeepingAllForfeitsEveryRecord() {
        val result = assertIs<List<*>>(mainResult(assertIs<Outcome.Success>(run("result-all")).value))
        assertEquals((0L until 5L).toList(), result.map { ((it as Map<*, *>)["id"] as Number).toLong() })
        assertAllForfeited()
    }


    @Test
    fun runWorkerBoundaryForfeitsTheRecordItsChildReturns() {
        assertIs<Outcome.Success>(run("run"))
        assertEquals((0L until 5L).toList(), sinkIds())
        assertAllForfeited()
    }


    @Test
    fun javaTransformBoundaryForfeitsTheRecordPluginCodeMayKeep() {
        assertIs<Outcome.Success>(run("java"))
        assertEquals((0L until 5L).toList(), sinkIds())
        assertAllForfeited()
    }


    @Test
    fun sinkFailingMidBatchRecyclesWhatItReadAndLeaksTheRemainder() {
        val failed = assertIs<Outcome.Failed>(run("sink-failure"))
        assertTrue(failed.toString().contains("recycle fixture failure at 3"), failed.toString())
        // Three callbacks returned and the failing one's lease was released; the rest of the batch leaks
        assertEquals(4, recycles())
        assertEquals(0, forfeited())
    }


    @Test
    fun transformFailingMidBatchRecyclesOnlyTheFailingElement() {
        val failed = assertIs<Outcome.Failed>(run("transform-failure"))
        assertTrue(failed.toString().contains("recycle fixture failure"), failed.toString())
        // The earlier outputs share their records and wait unflushed in the Formula's output: they leak with it
        assertEquals(1, recycles())
        assertEquals(0, forfeited())
    }


    @Test
    fun cancellingWithValuesInFlightRecyclesNothing() {
        val engine = newEngine(document("cancel"))
        try {
            engine.resume()
            // Two batches of eight buffered and one parked mid-flush behind the gated sink
            awaitStable("the source parked") { RecyclingSourceWorker.acquired.get() == 24 }
            engine.cancel()
            assertIs<Outcome.Cancelled>(runBlocking { withTimeout(hangGuardMillis) { engine.await() } })
        }
        finally {
            engine.close()
        }
        assertEquals(0, recycles())
        assertEquals(24, RecyclingSourceWorker.created.size)
    }


    @Test
    fun poolStaysAtTheInFlightCountOverALongStream() {
        assertIs<Outcome.Success>(run("bounded"))
        assertEquals((0L until 20_000L).toList(), sinkIds())
        assertAllRecycled()
        val created = RecyclingSourceWorker.created.size
        println("WR2 pool: $created records created for ${RecyclingSourceWorker.acquired.get()} acquired")
        // At most fifteen batches of 16 are in flight: per channel two buffered and one parked, plus each Worker's
        // batch in hand and its unflushed output; the bound leaves slack for that count, not for the stream length
        assertTrue(created <= 20 * 16, "created $created")
    }


    @Test
    fun liveEditCarriesBufferedAndParkedRecordsAndTheNewSinkRecyclesThem() {
        val documentPath = document("migration-buffered")
        migrate(documentPath) {
            // Two batches of four buffered and one parked mid-flush behind the gated sink
            awaitStable("the source parked") { RecyclingSourceWorker.acquired.get() == 12 }
        }
        assertEquals((0L until 40L).toList(), sinkIds(), "every record once, in order")
        assertEquals(2, RecyclingSourceWorker.pools.size, "the edit replaced the source")
        assertAllRecycled()
    }


    @Test
    fun liveEditResumesAnExpandingTransformMidBatchOnItsCarriedHolds() {
        val documentPath = document("migration-expanding")
        migrate(documentPath) {
            // The fan-out expands its first batch while the second is buffered and the third parked
            awaitStable("the pipeline parked") { RecyclingSourceWorker.acquired.get() == 12 }
        }
        val expected = (0 until 16).flatMap { id -> (0 until 3).map { "$id-$it" } }
        assertEquals(expected, RecyclableSinkWorker.of("sink"), "every output once, in order")
        assertAllRecycled()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun recycles(): Int =
        RecyclingSourceWorker.created.sumOf { it.generation }


    private fun forfeited(): Int =
        RecyclingSourceWorker.created.count { it.isForfeited }


    private fun assertAllRecycled() {
        assertEquals(RecyclingSourceWorker.acquired.get(), recycles(), "every record returned to a pool once")
        assertEquals(0, forfeited())
    }


    private fun assertAllForfeited() {
        val created = RecyclingSourceWorker.created
        assertEquals(RecyclingSourceWorker.acquired.get(), created.size, "nothing was reused")
        assertTrue(created.all { it.isForfeited })
        assertEquals(0, recycles())
    }


    private fun sinkIds(): List<Long> =
        RecyclableSinkWorker.of("sink").map { ((it as Map<*, *>)["id"] as Number).toLong() }


    private fun mainResult(bindings: DataBindings): Any? {
        val bound = assertIs<BindingState.Bound>(bindings[BindingName("main")])
        return JobDataValues.boundary(bound.value)
    }


    private fun progressOf(engine: RunEngine, key: String): Map<*, *> =
        engine.snapshot().root.children
            .mapNotNull { it.live[Address.of("\$job-progress")]?.get() as? Map<*, *> }
            .first { it.containsKey(key) }


    // Waits for the condition, then checks it still holds after the pipeline had time to move on
    private fun awaitStable(what: String, condition: () -> Boolean) {
        val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(hangGuardMillis)
        while (!condition()) {
            assertTrue(System.nanoTime() < deadlineNanos, "timed out waiting for $what")
            Thread.sleep(10)
        }
        Thread.sleep(settleMillis)
        assertTrue(condition(), "$what, and stayed")
    }


    private fun migrate(documentPath: DocumentPath, awaitInFlight: () -> Unit) {
        context = KzenAutoContext.forTest()
        val jobLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))
        val sinkLocation = ObjectLocation(documentPath, ObjectPath.parse("main.workers/sink"))
        val notation = AutoTestUtils.readNotation()
        val baseLogic = compile(jobLocation, notation)
        val editedLogic = compile(jobLocation, edit(notation, sinkLocation))
        val engine = RunEngine(baseLogic, context.objectStableMapper.objectStableId(jobLocation))
        try {
            engine.resume()
            awaitInFlight()
            engine.pause()
            engine.awaitQuiescent()
            assertEquals(0, recycles(), "nothing was released before the cut")
            assertTrue(RecyclingSourceWorker.created.all { it.holds() == 1 }, "each in-flight record held once")
            engine.migrate(editedLogic, paused = false)
            assertIs<Outcome.Success>(runBlocking { withTimeout(hangGuardMillis) { engine.await() } })
        }
        finally {
            engine.close()
        }
        assertEquals(2, RecyclableSinkWorker.instances.get(), "the gated sink was replaced")
    }


    private fun document(route: String): DocumentPath =
        DocumentPath.parse("test/job/recycle/recycle-$route.yaml")


    private fun run(route: String): Outcome {
        val engine = newEngine(document(route))
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


    private fun edit(notation: GraphNotation, objectLocation: ObjectLocation): GraphNotation =
        NotationReducer()
            .applyStructural(
                notation,
                UpsertAttributeCommand(objectLocation, AttributeName("note"), ScalarAttributeNotation("edited")))
            .graphNotation
}
