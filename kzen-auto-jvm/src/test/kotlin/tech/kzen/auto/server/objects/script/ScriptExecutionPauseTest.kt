package tech.kzen.auto.server.objects.script

import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.logic.LogicExecution
import tech.kzen.lib.common.exec.logic.LogicExecutionFacade
import tech.kzen.lib.common.exec.logic.LogicHandle
import tech.kzen.lib.common.exec.logic.model.LogicPauseReason
import tech.kzen.lib.common.exec.logic.model.LogicResultPaused
import tech.kzen.lib.common.exec.logic.model.LogicResultSuccess
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
 * Coverage for [tech.kzen.auto.server.objects.script.step.control.PauseStep]: a step that pauses the
 * run on first execution (returns [LogicResultPaused]) and proceeds on resume (returns
 * [LogicResultSuccess], here ending the single-step run). Drives the real
 * notation -> graph -> ScriptExecution path in-process, so it needs no server and no SUT subprocess.
 * The resumed flag survives the per-resume graph rebuild via StatefulLogicElement.loadState.
 */
class ScriptExecutionPauseTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val documentPath = DocumentPath.parse("test/pause-step-test.yaml")
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
    fun pausesOnFirstRun() {
        val execution = newExecution()
        val result = execution.continueOrStart(
            MutableLogicControl(false), MutableLogicResourceScope(), graphDefinition())
        assertEquals(LogicResultPaused(LogicPauseReason.Explicit), result)
    }


    @Test
    fun resumeProceedsToCompletion() {
        // The same execution is reused across calls: ScriptExecution rebuilds the graph each resume and
        // transfers the PauseStep's resumed flag via StatefulLogicElement.loadState, so the second run
        // proceeds past the pause and completes the single-step script.
        val execution = newExecution()
        val control = MutableLogicControl(false)
        val resourceScope = MutableLogicResourceScope()
        val graphDefinition = graphDefinition()

        assertEquals(LogicResultPaused(LogicPauseReason.Explicit), execution.continueOrStart(control, resourceScope, graphDefinition))
        assertIs<LogicResultSuccess>(execution.continueOrStart(control, resourceScope, graphDefinition))
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
    // A single PauseStep neither starts nor nests another logic, so the handle is never queried.
    private object UnusedLogicHandle: LogicHandle {
        override fun start(
            logicRunExecutionId: LogicRunExecutionId,
            originalObjectLocation: ObjectLocation,
            callerLocation: ObjectLocation?
        ): LogicExecutionFacade =
            error("nested logic should not start for a single pause step")
    }
}
