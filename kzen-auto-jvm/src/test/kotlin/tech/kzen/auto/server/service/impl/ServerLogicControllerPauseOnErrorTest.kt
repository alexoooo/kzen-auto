package tech.kzen.auto.server.service.impl

import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.logic.run.model.LogicRunId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunState
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.service.notation.NotationReducer
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail


/**
 * Integration coverage for per-run "pause on error" on the new [tech.kzen.lib.server.exec.engine.RunEngine],
 * driven through the PUBLIC [ServerLogicController] surface — the clean-room successor to the old
 * [tech.kzen.auto.server.objects.script.ScriptExecutionPauseOnErrorTest] (MultiStep's failed-step branch). The
 * engine mechanism is [tech.kzen.lib.common.exec.engine.Execution.recoverable], wired into the Script flavour's
 * [tech.kzen.auto.server.exec.script.ScriptRunContext]; the engine-level mechanics are proven in isolation by
 * [tech.kzen.lib.server.exec.engine.RunEngineTest].
 *
 * Uses the single-failing-step fixture (`Boom` throws). With pause-on-error OFF the run ends; with it ON the run
 * settles [LogicRunState.ErrorPaused] (non-terminal) at the failed step for inspect / fix + resume — and editing
 * the step to a working expression then resuming completes the run, exercising pause-on-error and the live-edit
 * migrate path together (the realistic fix-and-resume flow).
 */
class ServerLogicControllerPauseOnErrorTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val documentPath = DocumentPath.parse("test/pause-on-error-test.yaml")
    private val mainLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))
    private val boomLocation = ObjectLocation(documentPath, ObjectPath.parse("main.steps/Boom"))

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
    fun failingStepEndsRunWhenPauseOnErrorDisabled() {
        val controller = context.serverLogicController
        val runId = controller.start(mainLocation, snapshot, false)
            ?: fail("Unable to start run")

        controller.continueOrStart(runId, snapshot)

        // The failing step settles the run (terminal), never pausing — so the run goes inactive. Boom always
        // throws, so a terminated run here is a failed one (it cannot have succeeded).
        awaitInactive()
        assertNull(controller.status().active, "pause-on-error off: the failing step ends the run")
    }


    @Test
    fun failingStepPausesAtErrorPausedWhenPauseOnErrorEnabled() {
        val controller = context.serverLogicController
        val runId = controller.start(mainLocation, snapshot, true)
            ?: fail("Unable to start run")

        controller.continueOrStart(runId, snapshot)

        // The failing step parks the run Suspended(Error) — non-terminal, still active, distinctly ErrorPaused
        // (not a plain Boundary pause) — for inspect / fix + resume.
        awaitState(LogicRunState.ErrorPaused)
        assertEquals(runId, controller.status().active?.id, "the run stays active, paused on the failed step")
    }


    @Test
    fun fixingErrorPausedStepAndResumingCompletesViaMigrate() {
        val controller = context.serverLogicController
        val runId = controller.start(mainLocation, snapshot, true)
            ?: fail("Unable to start run")

        controller.continueOrStart(runId, snapshot)
        awaitState(LogicRunState.ErrorPaused)

        // Fix the failing step (replace the throwing expression with a working one) and resume: the controller
        // detects the edit, migrates, and the previously-failed step — never recorded as completed — re-runs
        // against the fix and the run completes.
        val fixed = AutoTestUtils.graphDefinitionAttempt(
            edit(AutoTestUtils.readNotation(), boomLocation, "code", "42"))
        controller.continueOrStart(runId, fixed)

        awaitInactive()
        assertNull(controller.status().active, "the fixed step ran and the run completed")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val snapshot: GraphDefinitionAttempt
        get() = AutoTestUtils.graphDefinitionAttempt(AutoTestUtils.readNotation())


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


    @Suppress("SameParameterValue")
    private fun awaitState(state: LogicRunState) {
        for (attempt in 0 until 500) {
            if (context.serverLogicController.status().active?.state == state) {
                return
            }
            Thread.sleep(10)
        }
        fail("Run did not reach $state (was ${context.serverLogicController.status().active?.state})")
    }


    private fun awaitInactive() {
        for (attempt in 0 until 500) {
            if (context.serverLogicController.status().active == null) {
                return
            }
            Thread.sleep(10)
        }
        fail("Run did not go inactive")
    }
}
