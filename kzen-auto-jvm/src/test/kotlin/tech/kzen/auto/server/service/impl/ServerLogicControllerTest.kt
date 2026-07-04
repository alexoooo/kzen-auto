package tech.kzen.auto.server.service.impl

import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.objects.document.script.model.StepTrace
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.logic.run.model.LogicRunId
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
 * the engine end-to-end, [ServerLogicController.status] reflects the engine snapshot, and the engine's emitted
 * trace events are bridged back into the [tech.kzen.lib.server.exec.logic.trace.LogicTraceStore] the client
 * reads.
 */
class ServerLogicControllerTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val foreachPath = DocumentPath.parse("test/script-engine-foreach-test.yaml")
    private val foreachMain = ObjectLocation(foreachPath, ObjectPath.parse("main"))
    private val foreachResult = ObjectLocation(foreachPath, ObjectPath.parse("main.steps/Result"))

    private val waitPath = DocumentPath.parse("test/script-engine-literal-wait-test.yaml")
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
        val traceSnapshot = context.logicTraceStore.lookupRun(runId, LogicTraceQuery(LogicTracePath.root))
            ?: fail("No run trace")
        val resultStableId = context.objectStableMapper.objectStableId(foreachResult)
        val resultEntry = traceSnapshot.values[LogicTracePath.ofObjectStableId(resultStableId)]
        assertNotNull(resultEntry, "Result step value not bridged into trace store")
        val resultTrace = StepTrace.ofExecutionValue(resultEntry.value)
        assertEquals(StepTrace.State.Done, resultTrace.state)
        assertEquals("12", resultTrace.displayValue.get())

        // The "next to run" highlight is cleared once the run completes.
        val nextStep = traceSnapshot.values[ScriptConventions.nextStepTracePath]
        assertNull(nextStep?.value?.get(), "Next-step highlight should be cleared after completion")
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

        val traceSnapshot = context.logicTraceStore.lookupRun(runId, LogicTraceQuery(LogicTracePath.root))
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
