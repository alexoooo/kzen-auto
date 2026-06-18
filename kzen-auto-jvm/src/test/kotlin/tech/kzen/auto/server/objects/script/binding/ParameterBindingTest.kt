package tech.kzen.auto.server.objects.script.binding

import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.objects.script.ScriptValidator
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.logic.LogicExecution
import tech.kzen.lib.common.exec.logic.LogicExecutionFacade
import tech.kzen.lib.common.exec.logic.LogicHandle
import tech.kzen.lib.common.exec.logic.model.LogicResultSuccess
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.exec.tuple.TupleComponentValue
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.platform.ClassNames
import tech.kzen.lib.server.exec.logic.context.MutableLogicControl
import tech.kzen.lib.server.exec.logic.context.MutableLogicResourceScope
import kotlin.test.assertEquals
import kotlin.test.assertIs


/**
 * A typed Script parameter is exposed to steps by name without an ArgumentStep body row: a
 * [tech.kzen.auto.server.objects.script.binding.ParameterBinding] in the `parameters` branch is validated
 * (carries its declared TypeMetadata), is in scope for every step, and resolves its value from the run
 * arguments on demand. Covers both type inference (validation) and value resolution (execution).
 */
class ParameterBindingTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val documentPath = DocumentPath.parse("test/parameter-binding-test.yaml")
    private val mainLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))

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
    fun parameterDeclaresItsType() {
        assertEquals(
            TypeMetadata(ClassNames.kotlinInt, listOf(), false),
            typeMetadataFor("main.parameters/threshold"))
    }


    @Test
    fun parameterDeclaresGenericType() {
        assertEquals(
            TypeMetadata(
                ClassNames.kotlinList,
                listOf(TypeMetadata(ClassNames.kotlinString, listOf(), false)),
                false),
            typeMetadataFor("main.parameters/tags"))
    }


    @Test
    fun formulaInfersTypeFromParameterByName() {
        // `threshold * 2` over an Int parameter, referenced by name, with no ArgumentStep row.
        assertEquals(
            TypeMetadata(ClassNames.kotlinInt, listOf(), false),
            typeMetadataFor("main.steps/Doubled"))

        // `tags.first()` over a List<String> parameter resolves the element type.
        assertEquals(
            TypeMetadata(ClassNames.kotlinString, listOf(), false),
            typeMetadataFor("main.steps/FirstTag"))
    }


    @Test
    fun formulaResolvesParameterValueByName() {
        val execution = newExecution()

        execution.beforeStart(TupleValue(listOf(
            TupleComponentValue(TupleComponentName("threshold"), 21),
            TupleComponentValue(TupleComponentName("tags"), listOf("alpha", "beta")))))

        val result = execution.continueOrStart(
            MutableLogicControl(false), MutableLogicResourceScope(), graphDefinition())

        // The script's last step is FirstTag (`tags.first()`); its value is the run result's main value.
        val success = assertIs<LogicResultSuccess>(result)
        assertEquals("alpha", success.value.mainComponentValue())
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun typeMetadataFor(stepObjectPath: String): TypeMetadata? {
        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinitionAttempt = AutoTestUtils.graphDefinitionAttempt(graphNotation)

        val stepGraphDefinition = graphDefinitionAttempt
            .transitiveSuccessful
            .filterTransitive(documentPath)

        val graphInstance = GraphCreator.createGraph(stepGraphDefinition, context.graphEnvironment)

        val scriptValidation = ScriptValidator.validate(
            documentPath,
            graphNotation,
            stepGraphDefinition,
            graphInstance)

        return scriptValidation.stepValidations[ObjectPath.parse(stepObjectPath)]?.typeMetadata
    }


    private fun graphDefinition() =
        AutoTestUtils.graphDefinitionAttempt(AutoTestUtils.readNotation())
            .transitiveSuccessful


    private fun newExecution(): LogicExecution {
        return AutoTestUtils.liveLogicExecution(context, mainLocation, UnusedLogicHandle)
    }


    //-----------------------------------------------------------------------------------------------------------------
    // No nested logic in this script, so the handle is never queried.
    private object UnusedLogicHandle: LogicHandle {
        override fun start(
            logicRunExecutionId: LogicRunExecutionId,
            originalObjectLocation: ObjectLocation
        ): LogicExecutionFacade =
            error("nested logic should not start")
    }
}
