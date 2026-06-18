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
 * A MappingStep's loop item is exposed to nested steps by name without a body row: a
 * [tech.kzen.auto.server.objects.script.binding.MappingItemBinding] in the mapping's `item` branch is
 * typed to the element type of the items collection (inferred via the mapping's List output), is in
 * scope for the loop body, and resolves the current iteration value on demand.
 */
class MappingItemBindingTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val documentPath = DocumentPath.parse("test/mapping-item-binding-test.yaml")
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
    fun mappingInfersListElementType() {
        assertEquals(
            TypeMetadata(ClassNames.kotlinList, listOf(TypeMetadata.int), false),
            typeMetadataFor("main.steps/Loop"))
    }


    @Test
    fun itemBindingHasElementType() {
        assertEquals(
            TypeMetadata(ClassNames.kotlinInt, listOf(), false),
            typeMetadataFor("main.steps/Loop.item/Item"))
    }


    @Test
    fun bodyFormulaInfersTypeFromItemByName() {
        // `Item * 2` over an Int loop item, referenced by name, with no MappingItemStep row.
        assertEquals(
            TypeMetadata(ClassNames.kotlinInt, listOf(), false),
            typeMetadataFor("main.steps/Loop.steps/Doubled"))
    }


    @Test
    fun bodyFormulaResolvesItemValueByName() {
        val execution = newExecution()
        execution.beforeStart(TupleValue.empty)

        val result = execution.continueOrStart(
            MutableLogicControl(false), MutableLogicResourceScope(), graphDefinition())

        val success = assertIs<LogicResultSuccess>(result)
        assertEquals(listOf(2, 4, 6), success.value.mainComponentValue())
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
    private object UnusedLogicHandle: LogicHandle {
        override fun start(
            logicRunExecutionId: LogicRunExecutionId,
            originalObjectLocation: ObjectLocation
        ): LogicExecutionFacade =
            error("nested logic should not start")
    }
}
