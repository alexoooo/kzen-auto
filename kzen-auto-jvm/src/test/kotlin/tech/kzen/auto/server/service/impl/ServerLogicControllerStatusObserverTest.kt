package tech.kzen.auto.server.service.impl

import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail


/**
 * Covers the push transport's server half: [ServerLogicController.observeStatus] is what /logic/events streams
 * from, so anything it fails to announce is a status the UI never learns about until its fallback poll.
 *
 * The subtle case, and the reason this test exists, is the SETTLE. The engine publishes its park BEFORE
 * [ServerLogicController.settleAfterDrive] runs (that only happens once awaitQuiescent has returned), and at
 * that moment `stepping` is still set — so the engine-sourced signal reports Stepping, not Paused. Only the
 * controller can announce the Stepping -> Paused transition, which is precisely the edge an interactive client
 * and the slow-motion loop wait on. Without it the UI sits on "Stepping" until the fallback poll corrects it.
 *
 * Also pins the [LogicStatus] versioning the whole no-refetch-unless-changed design rests on: a monotone epoch
 * for transitions no run sequence can express, and the run's trace high-water for those it can.
 */
class ServerLogicControllerStatusObserverTest {
    //-----------------------------------------------------------------------------------------------------------------
    // A Script whose leaf is a Pause step, so the run reliably settles instead of racing to terminal.
    private val scriptPath = DocumentPath.parse("test/nested-depth-test.yaml")
    private val scriptMain = ObjectLocation(scriptPath, ObjectPath.parse("main"))

    private lateinit var context: KzenAutoContext


    //-----------------------------------------------------------------------------------------------------------------
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
    fun statusObserverAnnouncesRunStartAndSettle() {
        val controller = context.serverLogicController
        val snapshot = graphDefinitionAttempt()

        // Record the status each notification could actually have observed. A bare count would prove nothing
        // about the settle — the run start and every engine emit already push the count above zero, so
        // "count > 0 after settling" passes even with the settle announcement deleted.
        //
        // Reading status() inside a listener deliberately breaks the production contract (it belongs on the
        // hot path and the monitor is sometimes already held) but is safe HERE: the monitor is reentrant, so
        // the in-lock callers just re-enter on the same thread, and engine-thread callers simply take it.
        // Production listeners hand off instead — /logic/events only does a trySend.
        val observed = java.util.concurrent.ConcurrentLinkedQueue<String>()
        val subscription = controller.observeStatus {
            observed.add(controller.status().active?.state?.name ?: "no-run")
        }

        try {
            // Starting a run must announce itself: nothing else would tell a connected client a run now exists.
            val runId = controller.start(scriptMain, snapshot)
                ?: fail("Unable to start run")
            assertTrue(
                observed.isNotEmpty(),
                "start() must announce the new run")

            controller.continueOrStart(runId, snapshot)
            awaitSettled(controller)

            val settledState = assertNotNull(controller.status().active).state
            assertTrue(
                ! settledState.isExecuting(),
                "run should have settled, was $settledState")

            // THE regression guard: some notification must have carried the settled state. Delete the notify
            // from settleAfterDrive and every recorded status reads Running/Stepping — which is exactly the
            // bug this covers: the client is never told the step finished.
            assertTrue(
                observed.any { it == settledState.name },
                "the settle must be announced — the engine's own publish still reports Stepping. " +
                        "Observed: $observed")
        }
        finally {
            subscription.close()
        }
    }


    @Test
    fun statusObserverUnsubscribes() {
        val controller = context.serverLogicController

        val notifications = AtomicInteger()
        val subscription = controller.observeStatus { notifications.incrementAndGet() }
        subscription.close()

        // An unclosed subscription would outlive its run and leak for the process's life, so closing must
        // actually detach — a browser tab closing is the common case.
        controller.start(scriptMain, graphDefinitionAttempt())
            ?: fail("Unable to start run")

        assertEquals(0, notifications.get(), "a closed subscription must receive nothing")
    }


    @Test
    fun statusCarriesMonotoneEpochAndRunSequence() {
        val controller = context.serverLogicController
        val snapshot = graphDefinitionAttempt()

        // No run has ever started: still a well-formed, versioned status.
        val initial = controller.status()
        assertEquals(null, initial.active)

        val runId = controller.start(scriptMain, snapshot)
            ?: fail("Unable to start run")

        val started = controller.status()
        assertTrue(
            started.epoch > initial.epoch,
            "starting a run must advance the epoch")
        assertTrue(
            started.structureVersion > initial.structureVersion,
            "starting a run must advance the structure version")
        assertEquals(runId, assertNotNull(started.active).id)

        controller.continueOrStart(runId, snapshot)
        awaitSettled(controller)

        // The run actually did work, so its trace high-water must have advanced past the start. This is the
        // value every client keys its per-emit trace re-fetch on: no advance would mean no refresh.
        val settled = controller.status()
        assertTrue(
            assertNotNull(settled.active).sequence > assertNotNull(started.active).sequence,
            "a run that executed must advance its trace sequence")

        // The run built out its (nested) execution tree and reached a new run-state, so the structure version
        // must have advanced too — the exact signal that keeps traced / lookupRunExecutions re-fetching.
        assertTrue(
            settled.structureVersion > started.structureVersion,
            "a run that executed must advance its structure version")

        // ...but a structure version, unlike the retired wall clock, must NOT move on its own: two back-to-back
        // reads of an unchanged (settled) run are identical. This stability is what stops the per-poll re-fetch
        // storm — a structure-keyed consumer holding this value has, by construction, nothing new to fetch.
        val reread = controller.status()
        assertEquals(
            settled.structureVersion, reread.structureVersion,
            "structure version must not advance without an actual structural change")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun graphDefinitionAttempt(): GraphDefinitionAttempt {
        return AutoTestUtils.graphDefinitionAttempt(AutoTestUtils.readNotation())
    }


    private fun awaitSettled(controller: ServerLogicController) {
        for (attempt in 0 until 500) {
            val state = controller.status().active?.state
            if (state != null && ! state.isExecuting()) {
                return
            }
            Thread.sleep(10)
        }
        fail("Run did not settle")
    }
}
