package tech.kzen.auto.server.objects.script

import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.logic.LogicExecution
import tech.kzen.lib.common.exec.logic.LogicExecutionFacade
import tech.kzen.lib.common.exec.logic.LogicHandle
import tech.kzen.lib.common.exec.logic.model.LogicResultFailed
import tech.kzen.lib.common.exec.logic.model.LogicResultPaused
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.server.exec.logic.context.MutableLogicControl
import tech.kzen.lib.server.exec.logic.context.MutableLogicResourceScope
import kotlin.test.assertEquals
import kotlin.test.assertIs


/**
 * Coverage for per-run "pause on error" — the failed-step branch of [MultiStep] (reached
 * here via [ScriptExecution] over the Script root) and the convention that an Error step stays
 * runnable on resume.
 *
 * A failing step run with pauseOnError=true returns [LogicResultPaused] instead of ending the run;
 * the same step run without the flag returns [LogicResultFailed]. This drives the real
 * notation -> graph -> ScriptExecution path in-process, so it needs no server and no SUT subprocess.
 */
class ScriptExecutionPauseOnErrorTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val documentPath = DocumentPath.parse("test/pause-on-error-test.yaml")
    private val mainLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))

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
    fun failingStepPausesWhenPauseOnError() {
        val execution = newExecution()
        val result = execution.continueOrStart(
            MutableLogicControl(pauseOnError = true), MutableLogicResourceScope(), graphDefinition())
        assertEquals(LogicResultPaused, result)
    }


    @Test
    fun failingStepEndsRunWhenNotPauseOnError() {
        val execution = newExecution()
        val result = execution.continueOrStart(
            MutableLogicControl(pauseOnError = false), MutableLogicResourceScope(), graphDefinition())
        assertIs<LogicResultFailed>(result)
    }


    @Test
    fun pausedErrorStepReRunsOnResume() {
        // The error-paused step is left in Error (not Done), so nextToRun picks it again: resuming
        // re-runs the still-failing step and pauses anew rather than treating it as completed.
        val execution = newExecution()
        val control = MutableLogicControl(pauseOnError = true)
        val resourceScope = MutableLogicResourceScope()
        val graphDefinition = graphDefinition()

        assertEquals(LogicResultPaused, execution.continueOrStart(control, resourceScope, graphDefinition))
        assertEquals(LogicResultPaused, execution.continueOrStart(control, resourceScope, graphDefinition))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun graphDefinition(): GraphDefinition {
        val graphNotation = AutoTestUtils.readNotation()
        return AutoTestUtils.graphDefinitionAttempt(graphNotation).transitiveSuccessful
    }


    private fun newExecution(): LogicExecution {
        // Drive the live ScriptDocument.execute path (services resolved from the environment via
        // @Service) rather than hand-constructing ScriptExecution.
        val execution = AutoTestUtils.liveLogicExecution(
            context, mainLocation, UnusedLogicHandle)

        execution.beforeStart(TupleValue.empty)
        return execution
    }


    //-----------------------------------------------------------------------------------------------------------------
    // A single FormulaStep neither starts nor nests another logic, so the handle is never queried.
    private object UnusedLogicHandle: LogicHandle {
        override fun start(
            logicRunExecutionId: LogicRunExecutionId,
            originalObjectLocation: ObjectLocation
        ): LogicExecutionFacade =
            error("nested logic should not start for a single failing step")
    }
}
