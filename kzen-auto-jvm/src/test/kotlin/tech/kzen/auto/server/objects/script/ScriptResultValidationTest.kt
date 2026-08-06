package tech.kzen.auto.server.objects.script

import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.common.objects.document.script.model.ScriptValidation
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.service.context.GraphCreator
import kotlin.test.assertContains
import kotlin.test.assertNotNull
import kotlin.test.assertNull


class ScriptResultValidationTest {
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
    fun implicitTypeMismatchIsAnErrorOnTheLastStep() {
        val validation = scriptValidationFor("test/script/result/implicit-result-mismatch-test.yaml")

        val error = assertNotNull(
            validation.stepValidations[ObjectPath.parse("main.steps/Tail")]?.errorMessage)

        assertContains(error, "Result declares Int but this step produces String")
    }


    @Test
    fun unitLastStepWithDeclaredResultIsAnError() {
        val validation = scriptValidationFor("test/script/result/implicit-result-unit-test.yaml")

        val error = assertNotNull(
            validation.stepValidations[ObjectPath.parse("main.steps/Show")]?.errorMessage)

        assertContains(error, "Result declares Int but this step produces no value")

        assertNull(
            validation.stepValidations[ObjectPath.parse("main.steps/Value")]?.errorMessage,
            "the Script ends on Show, so Value is not the step that owes the result")
    }


    @Test
    fun declaredResultWithNoStepsIsAnErrorOnMain() {
        val validation = scriptValidationFor("test/script/result/implicit-result-no-steps-test.yaml")

        val error = assertNotNull(
            validation.stepValidations[ObjectPath.parse("main")]?.errorMessage)

        assertContains(error, "Result declares Int but this Script has no steps.")
    }


    @Test
    fun stepsAfterARootResultStepWarnAndDoNotError() {
        val validation = scriptValidationFor("test/script/result/result-unreachable-test.yaml")

        for (tailObjectPath in listOf("main.steps/Tail", "main.steps/Tail 2")) {
            val stepValidation = assertNotNull(
                validation.stepValidations[ObjectPath.parse(tailObjectPath)], tailObjectPath)

            assertContains(
                assertNotNull(stepValidation.warningMessage, tailObjectPath),
                "Never runs — the Result step above ends the Script.")

            assertNull(stepValidation.errorMessage, "unreachable is advisory, it must not gate Run")
        }

        assertNull(
            validation.stepValidations[ObjectPath.parse("main.steps/Answer")]?.warningMessage,
            "the Result step itself runs")
    }


    @Test
    fun nestedResultStepDoesNotExemptTheTail() {
        val validation = scriptValidationFor("test/script/result/implicit-result-nested-unit-tail-test.yaml")

        val error = assertNotNull(
            validation.stepValidations[ObjectPath.parse("main.steps/Show")]?.errorMessage,
            "a Result step inside an If branch is a conditional early return: the fall-through path still " +
                    "owes the declared result")

        assertContains(error, "Result declares Int but this step produces no value")
    }


    @Test
    fun voidScriptWithATrailingValueStepHasNoFindings() {
        val validation = scriptValidationFor("test/script/result/implicit-result-void-test.yaml")

        val stepValidation = assertNotNull(
            validation.stepValidations[ObjectPath.parse("main.steps/Tail")])

        assertNull(stepValidation.errorMessage)
        assertNull(stepValidation.warningMessage)

        assertNull(validation.stepValidations[ObjectPath.parse("main")])
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun scriptValidationFor(documentPathString: String): ScriptValidation {
        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinitionAttempt = AutoTestUtils.graphDefinitionAttempt(graphNotation)

        val documentPath = DocumentPath.parse(documentPathString)

        val stepGraphDefinition = graphDefinitionAttempt
            .transitiveSuccessful
            .filterTransitive(documentPath)

        val graphInstance = GraphCreator.createGraph(stepGraphDefinition, context.graphEnvironment)

        return ScriptValidator.validate(
            documentPath,
            graphNotation,
            stepGraphDefinition,
            graphInstance,
            context.cachedKotlinCompiler)
    }
}
