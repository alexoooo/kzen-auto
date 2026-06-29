package tech.kzen.auto.server.objects.job

import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.objects.job.worker.test.GatedCountingSinkWorker
import tech.kzen.auto.server.objects.job.worker.test.GatedSourceWorker
import tech.kzen.auto.server.objects.job.worker.test.GatedWorkerTestModule
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.logic.LogicExecutionFacade
import tech.kzen.lib.common.exec.logic.LogicHandle
import tech.kzen.lib.common.exec.logic.model.LogicResult
import tech.kzen.lib.common.exec.logic.model.LogicResultPaused
import tech.kzen.lib.common.exec.logic.model.LogicResultSuccess
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.service.notation.NotationReducer
import tech.kzen.lib.server.exec.logic.context.MutableLogicControl
import tech.kzen.lib.server.exec.logic.context.MutableLogicResourceScope
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue


/**
 * Deterministic end-to-end coverage of the lossless CHANNEL carryover in [JobExecution]'s state migration
 * (pause / edit config / continue): a channel that, at the pause cut, still holds buffered payloads PLUS one a
 * producer is parked mid-send on must be carried into the rebuilt graph with no row dropped or replayed
 * ([JobChannel.drainBuffered] / [JobChannel.preload]).
 *
 * The hard part is reaching "buffer full + one send parked" deterministically and pausing exactly there. The
 * gated test Workers make it race-free:
 *
 * - [GatedCountingSinkWorker]'s first instance never drains (parks in onStart), so the channel buffer fills and
 *   [GatedSourceWorker] parks mid-send. This is a STABLE state — nothing can advance it — so the source's
 *   static send counter settles at exactly `buffer + 1` and the test pauses on that signal with no wall-clock
 *   guess. (The former free-running version of this test raced the pause against a 200k-row pipeline finishing,
 *   and was flaky on a fast machine.)
 * - An external duplex channel suspends [JobExecution]'s deadlock detection while the gated run is quiescent,
 *   so the run idles (rather than failing) until the pause lands.
 *
 * On resume the edit (a no-op change to the sink, leaving the source's config untouched) triggers the migrate:
 * the source resumes from its position and the rebuilt, ungated sink drains the carryover followed by the
 * remainder. The final count equals the source's total IFF the migration was lossless — a dropped in-flight
 * payload would fall short, a restart-instead-of-resume would overshoot.
 *
 * The channel snapshot/restore primitives this leans on are also covered in isolation by
 * [tech.kzen.auto.server.objects.job.channel.JobChannelCarryoverTest]; this test proves [JobExecution] wires
 * them through a real buffered+parked channel.
 */
class JobMigrationCarryoverTest {
    //-----------------------------------------------------------------------------------------------------------------
    // Must match main.channels/raw `buffer` and main.workers/source `total` in the fixture.
    private val channelBuffer = 4
    private val sourceTotal = 50

    private val documentPath = DocumentPath.parse("test/job-migration-carryover-test.yaml")
    private val mainLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))
    private val sinkLocation = ObjectLocation(documentPath, ObjectPath.parse("main.workers/sink"))

    private lateinit var context: KzenAutoContext
    private val runExecutionId = LogicRunExecutionId.random()


    //-----------------------------------------------------------------------------------------------------------------
    @Before
    fun setUp() {
        // The test Workers carry no @Reflect (the test source set has no KSP pass); register them manually into
        // the global registry the graph creator reads, before forTest() builds any graph.
        GatedWorkerTestModule.register()
        GatedSourceWorker.reset()
        GatedCountingSinkWorker.reset()

        context = KzenAutoContext.forTest()
    }


    @After
    fun tearDown() {
        context.close()
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun migrationCarriesBufferedAndParkedChannelPayloadsLosslessly() {
        val notation = AutoTestUtils.readNotation()
        val baseDefinition = definitionOf(notation)

        // The edit: a no-op change to the SINK's `note`. It leaves the source's config untouched (so the source
        // resumes from its position rather than restarting) while changing objectDefinitions enough to trip the
        // migrate.
        val editedDefinition = definitionOf(
            NotationReducer().applyStructural(
                notation,
                UpsertAttributeCommand(
                    sinkLocation, AttributeName("note"), ScalarAttributeNotation("edited")))
                .graphNotation)

        val execution = AutoTestUtils.liveLogicExecution(
            context, mainLocation, UnusedLogicHandle, runExecutionId)
        execution.beforeStart(TupleValue.empty)

        val control = MutableLogicControl(false)
        val resourceScope = MutableLogicResourceScope()

        // Free-run on a background thread: the gated sink (#1) never drains, so the source fills the buffer and
        // parks mid-send; the external duplex channel keeps the (now quiescent) run from declaring deadlock, so
        // it idles until the test pauses it.
        val firstResult = AtomicReference<LogicResult>()
        val firstError = AtomicReference<Throwable>()
        val runner = Thread {
            try {
                firstResult.set(execution.continueOrStart(control, resourceScope, baseDefinition))
            }
            catch (e: Throwable) {
                firstError.set(e)
            }
        }
        runner.isDaemon = true
        runner.start()

        // Deterministic: the source initiates exactly channelBuffer + 1 sends (channelBuffer buffered, one
        // parked) and then can go no further (the gated sink never makes room), so this settles and stays there.
        val deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (GatedSourceWorker.sendsStarted.get() < channelBuffer + 1) {
            firstError.get()?.let { throw AssertionError("run failed before reaching the parked state", it) }
            assertTrue(System.nanoTime() < deadlineNanos, "source never filled the channel buffer")
            Thread.sleep(1)
        }

        control.commandPause()
        runner.join(TimeUnit.SECONDS.toMillis(30))
        firstError.get()?.let { throw AssertionError("run failed", it) }
        assertIs<LogicResultPaused>(firstResult.get())

        assertEquals(
            (channelBuffer + 1).toLong(), GatedSourceWorker.sendsStarted.get().toLong(),
            "source parks after exactly buffer + 1 sends (buffer buffered, one parked mid-send)")
        assertEquals(
            0L, GatedCountingSinkWorker.received.get(),
            "the gated first sink instance consumed nothing before the migration")

        // Pause / edit / continue: migrate drains the channel (buffered + parked) and preloads it into the
        // rebuilt channel; the source resumes from its position (no re-send) and the now-ungated sink (#2)
        // drains the carryover followed by the remainder.
        control.commandUnpause()
        val finalResult = execution.continueOrStart(control, resourceScope, editedDefinition)
        assertIs<LogicResultSuccess>(finalResult)

        assertEquals(
            sourceTotal.toLong(), GatedCountingSinkWorker.received.get(),
            "every row delivered exactly once: a dropped in-flight payload would fall short of $sourceTotal, a " +
                "restart-instead-of-resume would exceed it")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun definitionOf(graphNotation: GraphNotation): GraphDefinition =
        AutoTestUtils.graphDefinitionAttempt(graphNotation)
            .transitiveSuccessful
            .filterTransitive(documentPath)


    //-----------------------------------------------------------------------------------------------------------------
    private object UnusedLogicHandle: LogicHandle {
        override fun start(
            logicRunExecutionId: LogicRunExecutionId,
            originalObjectLocation: ObjectLocation,
            callerLocation: ObjectLocation?
        ): LogicExecutionFacade =
            error("nested logic should not start for a Job")
    }
}
