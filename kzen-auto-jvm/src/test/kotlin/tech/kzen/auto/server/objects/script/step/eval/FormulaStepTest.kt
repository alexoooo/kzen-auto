package tech.kzen.auto.server.objects.script.step.eval

import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.objects.script.ScriptValidator
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.platform.ClassNames
import kotlin.test.assertEquals


class FormulaStepTest {
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
    fun infersStringType() {
        assertEquals(
            TypeMetadata(ClassNames.kotlinString, listOf(), false),
            typeMetadataFor("main.steps/StringFormula"))
    }


    @Test
    fun infersIntType() {
        assertEquals(
            TypeMetadata(ClassNames.kotlinInt, listOf(), false),
            typeMetadataFor("main.steps/IntFormula"))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun typeMetadataFor(stepObjectPath: String): TypeMetadata? {
        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinitionAttempt = AutoTestUtils.graphDefinitionAttempt(graphNotation)

        val documentPath = DocumentPath.parse("test/formula-step-type-inference-test.yaml")

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
}
