package tech.kzen.auto.server.objects.script.step.eval

import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.common.objects.document.script.model.ScriptValidation
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.objects.script.ScriptValidator
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.ClassNames
import kotlin.test.assertEquals
import kotlin.test.assertNull


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


    @Test
    fun infersIntRangeType() {
        // `1..100` infers to IntRange — recognized rather than falling back to Any (drives a ForEach's
        // loop-item element type; see ForEachItemBindingTest).
        assertEquals(
            TypeMetadata(ClassName("kotlin.ranges.IntRange"), listOf(), false),
            typeMetadataFor("main.steps/RangeFormula"))
    }


    @Test
    fun infersNullableType() {
        assertEquals(
            TypeMetadata(ClassNames.kotlinString, listOf(), true),
            typeMetadataFor("main.steps/NullableFormula"))
    }


    @Test
    fun infersGenericListType() {
        assertEquals(
            TypeMetadata(ClassNames.kotlinList, listOf(TypeMetadata(ClassNames.kotlinInt, listOf(), false)), false),
            typeMetadataFor("main.steps/ListFormula"))
    }


    @Test
    fun infersUnitType() {
        assertEquals(
            TypeMetadata(ClassNames.kotlinUnit, listOf(), false),
            typeMetadataFor("main.steps/UnitFormula"))
    }


    @Test
    fun infersLongType() {
        assertEquals(
            TypeMetadata(ClassNames.kotlinLong, listOf(), false),
            typeMetadataFor("main.steps/LongFormula"))
    }


    @Test
    fun typeOutsideTheVisibleSetApproximatesToAny() {
        // A Char is not a known-importable builtin and not registry-declared, so it approximates to Any rather
        // than crashing (the reachable TODO the old diagnostic-parsing inference hit).
        assertEquals(
            TypeMetadata(ClassNames.kotlinAny, listOf(), false),
            typeMetadataFor("main.steps/CharFormula"))
    }


    @Test
    fun nothingTypedExpressionCompilesCleanly() {
        // An expression whose whole type is Nothing (e.g. `error(...)`) must compile with NO validation error
        // and throw at run time — K2 refuses an inferred-Nothing declaration, so the probe codegen must use a
        // shape that never declares Nothing directly. The inferred type approximates to Any (like Char above).
        val validation = scriptValidationFor("test/formula-step-type-inference-test.yaml")
            .stepValidations[ObjectPath.parse("main.steps/NothingFormula")]
        assertNull(validation?.errorMessage,
            "a Nothing-typed expression is valid: it fails at run time, not validation time")
        assertEquals(
            TypeMetadata(ClassNames.kotlinAny, listOf(), false),
            validation?.typeMetadata)
    }


    @Test
    fun unresolvableDependencyGetsAnExplicitDiagnostic() {
        val validation = scriptValidationFor("test/script-unresolved-dependency-test.yaml")
        assertEquals(
            "Unresolved: circular or unavailable dependency",
            validation.stepValidations[ObjectPath.parse("main.steps/Dependent")]?.errorMessage)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun typeMetadataFor(stepObjectPath: String): TypeMetadata? {
        return scriptValidationFor("test/formula-step-type-inference-test.yaml")
            .stepValidations[ObjectPath.parse(stepObjectPath)]
            ?.typeMetadata
    }


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
            graphInstance)
    }
}
