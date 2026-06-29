package tech.kzen.auto.server.objects.script.step.control

import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.common.objects.document.script.model.StepValidation
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
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.server.exec.logic.context.MutableLogicControl
import tech.kzen.lib.server.exec.logic.context.MutableLogicResourceScope
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull


/**
 * An IfStep's `main` type is the join of its two branches' terminal-step types (the value MultiStep returns
 * is the taken branch's last step value). That makes the If referenceable by name from a downstream Kotlin
 * expression. Uniform branch types give a precise, assignable type; divergent ones widen to Any.
 */
class IfStepTest {
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
    fun branchJoinTypeIsReferenceable() {
        // Both branches are String, so the If is typed String and a downstream Result step can reference it.
        assertEquals(
            TypeMetadata.string,
            stepValidationFor("test/if-step-type-test.yaml", "main.steps/Branch")?.typeMetadata)

        // Referencing the If by name resolves (no "unresolved reference") and type-checks against String.
        assertNull(
            errorMessageFor("test/if-step-type-test.yaml", "main.steps/Result"))

        // n=1 -> then branch -> the Script result is the taken branch's value, read through the If reference.
        assertEquals("yes", runMainResult("test/if-step-type-test.yaml"))
    }


    @Test
    fun divergentBranchesWidenToAny() {
        // then=String, else=Int -> the join widens to Any.
        assertEquals(
            TypeMetadata.any,
            stepValidationFor("test/if-step-divergent-type-test.yaml", "main.steps/Branch")?.typeMetadata)

        // The If is still referenceable, but Any doesn't satisfy the declared String result: a meaningful
        // type-mismatch error, NOT an "unresolved reference".
        assertNotNull(
            errorMessageFor("test/if-step-divergent-type-test.yaml", "main.steps/Result"))
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


    private fun errorMessageFor(documentPathValue: String, stepObjectPath: String): String? =
        stepValidationFor(documentPathValue, stepObjectPath)?.errorMessage


    private fun stepValidationFor(documentPathValue: String, stepObjectPath: String): StepValidation? {
        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinitionAttempt = AutoTestUtils.graphDefinitionAttempt(graphNotation)

        val documentPath = DocumentPath.parse(documentPathValue)

        val stepGraphDefinition = graphDefinitionAttempt
            .transitiveSuccessful
            .filterTransitive(documentPath)

        val graphInstance = GraphCreator.createGraph(stepGraphDefinition, context.graphEnvironment)

        val scriptValidation = ScriptValidator.validate(
            documentPath, graphNotation, stepGraphDefinition, graphInstance)

        return scriptValidation.stepValidations[ObjectPath.parse(stepObjectPath)]
    }


    //-----------------------------------------------------------------------------------------------------------------
    // No nested logic in these scripts, so the handle is never queried.
    private object UnusedLogicHandle: LogicHandle {
        override fun start(
            logicRunExecutionId: LogicRunExecutionId,
            originalObjectLocation: ObjectLocation,
            callerLocation: ObjectLocation?
        ): LogicExecutionFacade =
            error("nested logic should not start")
    }
}
