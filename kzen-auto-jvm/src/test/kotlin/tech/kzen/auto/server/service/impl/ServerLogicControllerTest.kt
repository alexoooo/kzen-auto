package tech.kzen.auto.server.service.impl

import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.common.objects.document.script.model.StepTrace
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.logic.run.model.LogicRunId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunResponse
import tech.kzen.lib.common.exec.logic.run.model.LogicRunState
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceQuery
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail


/**
 * Integration coverage for [ServerLogicController] running a Script on the new
 * [tech.kzen.lib.server.exec.engine.RunEngine]: the control surface (start / continueOrStart / step) drives
 * the engine end-to-end, [ServerLogicController.status] reflects the engine snapshot, and the trace values the
 * client reads are served by projecting the engine at query time
 * ([tech.kzen.auto.server.exec.RunEngineLogicTrace]).
 */
class ServerLogicControllerTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val foreachPath = DocumentPath.parse("test/script/control/script-engine-foreach-test.yaml")
    private val foreachMain = ObjectLocation(foreachPath, ObjectPath.parse("main"))
    private val foreachFirstStep = ObjectLocation(foreachPath, ObjectPath.parse("main.steps/Range"))
    private val foreachResult = ObjectLocation(foreachPath, ObjectPath.parse("main.steps/Result"))

    private val waitPath = DocumentPath.parse("test/script/engine/script-engine-literal-wait-test.yaml")
    private val waitMain = ObjectLocation(waitPath, ObjectPath.parse("main"))
    private val waitResult = ObjectLocation(waitPath, ObjectPath.parse("main.steps/Result"))

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
    fun continueRunsToCompletionAndBridgesTrace() {
        val controller = context.serverLogicController
        val runId = controller.start(foreachMain, snapshot)
            ?: fail("Unable to start run")

        controller.continueOrStart(runId, snapshot)
        awaitDone()

        // The Result step's value (Loop.sum() == 12) reached the trace store via the engine -> trace bridge,
        // shaped as a StepTrace exactly as the client expects (Done state + display value).
        val traceSnapshot = context.logicTrace.lookupRun(runId, LogicTraceQuery(LogicTracePath.root))
            ?: fail("No run trace")
        val resultStableId = context.objectStableMapper.objectStableId(foreachResult)
        val resultEntry = traceSnapshot.values[LogicTracePath.ofObjectStableId(resultStableId)]
        assertNotNull(resultEntry, "Result step value not bridged into trace store")
        val resultTrace = StepTrace.ofExecutionValue(resultEntry.value)
        assertEquals(StepTrace.State.Done, resultTrace.state)
        assertEquals("12", resultTrace.displayValue.get())

        // The retired next-step reserved marker is never written: position rides the status frame instead.
        val nextStep = traceSnapshot.values[LogicTracePath(listOf("next-step"))]
        assertNull(nextStep, "Reserved next-step trace path should no longer exist")
    }


    @Test
    fun stepPausesWithFrameThenResumesToCompletion() {
        val controller = context.serverLogicController
        val runId = controller.start(foreachMain, snapshot)
            ?: fail("Unable to start run")

        controller.step(runId, snapshot)
        awaitState(LogicRunState.Paused)

        val active = controller.status().active
            ?: fail("Run is not active while paused")
        assertEquals(runId, active.id)
        assertEquals(foreachMain, active.frame.objectLocation)

        // Engine-owned position (checkpoint at:): parked at the first step's boundary, the frame names it.
        assertEquals(foreachFirstStep, active.frame.position)

        controller.continueOrStart(runId, snapshot)
        awaitDone()
    }


    @Test
    fun setBreakpointsParksRunExplicitPausedAtStep() {
        val controller = context.serverLogicController
        val runId = controller.start(foreachMain, snapshot)
            ?: fail("Unable to start run")

        // Breakpoint locations resolve to stable ids server-side; a full-speed run parks ExplicitPaused
        // at the breakpointed step, with the frame position naming it.
        assertEquals(LogicRunResponse.Submitted, controller.setBreakpoints(runId, listOf(foreachResult)))
        controller.continueOrStart(runId, snapshot)
        awaitState(LogicRunState.ExplicitPaused)

        val active = controller.status().active
            ?: fail("Run is not active while paused at breakpoint")
        assertEquals(foreachResult, active.frame.position)

        // Replace-set with empty clears; the run resumes past the boundary and completes.
        assertEquals(LogicRunResponse.Submitted, controller.setBreakpoints(runId, listOf()))
        controller.continueOrStart(runId, snapshot)
        awaitDone()
    }


    @Test
    fun waitStepDoesNotFalselyPauseAndRunsToCompletion() {
        // A WaitStep's coroutine `delay` frees its engine dispatch task; unless the engine dispatcher counts a
        // pending delay as in-flight, `awaitQuiescent` mistakes that idle for the quiescent wavefront and the
        // run false-settles to Paused mid-step (and, having already settled, never clears when the delay
        // elapses). Regression guard: the run must remain active through the wait and settle to Done — pre-fix
        // this hung in a spurious Paused and `awaitDone` would time out.
        val controller = context.serverLogicController
        val runId = controller.start(waitMain, snapshot)
            ?: fail("Unable to start run")

        controller.continueOrStart(runId, snapshot)
        awaitDone()

        val traceSnapshot = context.logicTrace.lookupRun(runId, LogicTraceQuery(LogicTracePath.root))
            ?: fail("No run trace")
        val resultStableId = context.objectStableMapper.objectStableId(waitResult)
        val resultEntry = traceSnapshot.values[LogicTracePath.ofObjectStableId(resultStableId)]
        assertNotNull(resultEntry, "Result step value not bridged into trace store")
        val resultTrace = StepTrace.ofExecutionValue(resultEntry.value)
        assertEquals(StepTrace.State.Done, resultTrace.state)
        assertEquals("hello", resultTrace.displayValue.get())
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val snapshot: GraphDefinitionAttempt
        get() = AutoTestUtils.graphDefinitionAttempt(AutoTestUtils.readNotation())


    private fun awaitState(state: LogicRunState) {
        for (attempt in 0 until 500) {
            if (context.serverLogicController.status().active?.state == state) {
                return
            }
            Thread.sleep(10)
        }
        fail("Run did not reach $state (was ${context.serverLogicController.status().active?.state})")
    }


    private fun awaitDone() {
        for (attempt in 0 until 500) {
            if (context.serverLogicController.status().active == null) {
                return
            }
            Thread.sleep(10)
        }
        fail("Run did not complete")
    }
}
