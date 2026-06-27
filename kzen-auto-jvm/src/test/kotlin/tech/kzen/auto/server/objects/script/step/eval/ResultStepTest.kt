package tech.kzen.auto.server.objects.script.step.eval

import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.objects.script.ScriptValidator
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.logic.LogicExecutionFacade
import tech.kzen.lib.common.exec.logic.LogicHandle
import tech.kzen.lib.common.exec.logic.model.LogicResultSuccess
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.server.exec.logic.context.MutableLogicControl
import tech.kzen.lib.server.exec.logic.context.MutableLogicResourceScope
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull


/**
 * The Script's result is the value of the last invoked Result step (VB-style), type-checked against the
 * declared result signature. Covers value capture (execution), last-wins, void when no Result step runs,
 * and the two validation errors (type mismatch, missing signature).
 */
class ResultStepTest {
    //-----------------------------------------------------------------------------------------------------------------
    private lateinit var context: KzenAutoContext


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
    fun resultStepValueIsTheScriptResult() {
        // Base=21, Result=Base+21=42, then a trailing "ignored tail" FormulaStep: the run result is the
        // Result step's value, not the last body step's value.
        assertEquals(42, runMainResult("test/result-step-test.yaml"))
    }


    @Test
    fun lastInvokedResultWins() {
        assertEquals(2, runMainResult("test/result-step-last-wins-test.yaml"))
    }


    @Test
    fun noResultStepIsVoid() {
        // Declared result but no Result step ran -> void (null main), not the last step's value (99).
        assertNull(runMainResult("test/result-step-void-test.yaml"))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun typeMismatchIsValidationError() {
        assertNotNull(
            errorMessageFor("test/result-step-type-mismatch-test.yaml", "main.steps/Result"))
    }


    @Test
    fun noSignatureIsValidationError() {
        assertEquals(
            "No result type declared in the Script signature",
            errorMessageFor("test/result-step-no-signature-test.yaml", "main.steps/Result"))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun runMainResult(documentPathValue: String): Any? {
        val documentPath = DocumentPath.parse(documentPathValue)
        val mainLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))

        val execution = AutoTestUtils.liveLogicExecution(context, mainLocation, UnusedLogicHandle)
        execution.beforeStart(TupleValue.empty)

        val graphDefinition = AutoTestUtils
            .graphDefinitionAttempt(AutoTestUtils.readNotation())
            .transitiveSuccessful

        val result = execution.continueOrStart(
            MutableLogicControl(false), MutableLogicResourceScope(), graphDefinition)

        val success = assertIs<LogicResultSuccess>(result)
        return success.value.mainComponentValue()
    }


    private fun errorMessageFor(documentPathValue: String, stepObjectPath: String): String? {
        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinitionAttempt = AutoTestUtils.graphDefinitionAttempt(graphNotation)

        val documentPath = DocumentPath.parse(documentPathValue)

        val stepGraphDefinition = graphDefinitionAttempt
            .transitiveSuccessful
            .filterTransitive(documentPath)

        val graphInstance = GraphCreator.createGraph(stepGraphDefinition, context.graphEnvironment)

        val scriptValidation = ScriptValidator.validate(
            documentPath, graphNotation, stepGraphDefinition, graphInstance)

        return scriptValidation.stepValidations[ObjectPath.parse(stepObjectPath)]?.errorMessage
    }


    //-----------------------------------------------------------------------------------------------------------------
    // No nested logic in these scripts, so the handle is never queried.
    private object UnusedLogicHandle: LogicHandle {
        override fun start(
            logicRunExecutionId: LogicRunExecutionId,
            originalObjectLocation: ObjectLocation
        ): LogicExecutionFacade =
            error("nested logic should not start")
    }
}
