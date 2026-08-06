package tech.kzen.auto.server.objects.script.step.context

import tech.kzen.auto.common.objects.document.script.model.ScriptValidation
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.objects.script.ScriptValidator
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.context.GraphCreator
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


/**
 * What the generic context steps say at DEFINITION time, before a run exists to be wrong at.
 *
 * `ScriptContextValidationTest` covers the notation walk ([LogicContextAnalysis][tech.kzen.auto.common.objects
 * .document.logic.context.LogicContextAnalysis]) — availability, exports, dangling declarations — which is a
 * different mechanism from the one here: these verdicts come from each step's own `definition()`, so they need
 * the graph instantiated and the expression compiler running, and they arrive through
 * [ScriptValidator] rather than through the analysis.
 *
 * Two concerns, both of which only a concrete instantiation can reach:
 *
 * - [aBindWhoseExpressionCannotBeBoundIsRejectedAtItsOwnStep] and
 *   [aBindWhoseExpressionCanBeNullIsRejectedAgainstANonNullableContext] — the static half of the conformance
 *   the runtime bind re-checks by raw class. Caught here, the mismatch names both types at the expression that
 *   caused it; missed, it surfaces as a `ClassCastException` inside whatever step later read the Context.
 * - [anUnconfiguredBindStepDefinesAndAsksForAContext],
 *   [anUnconfiguredUseContextStepDefinesAndAsksForAContext] and
 *   [aDeclarationNamingNothingIsReportedAsABrokenName] — what a freshly-inserted step says. The empty-string
 *   declaration default has to DEFINE before it can be validated at all, so each asserts the object survives
 *   definition and only then asserts what it says. The two messages are deliberately different: an absent
 *   declaration asks the author to choose, a broken one names what failed to resolve, because the fixes differ.
 */
class ContextStepValidationTest {
    //-----------------------------------------------------------------------------------------------------------------
    private lateinit var context: KzenAutoContext


    @BeforeTest
    fun setUp() {
        context = KzenAutoContext.forTest()
    }


    @AfterTest
    fun tearDown() {
        context.close()
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun aBindWhoseExpressionCannotBeBoundIsRejectedAtItsOwnStep() {
        val error = errorOf("test/script/context/script-context-bind-mismatch-test.yaml", "main.steps/Bind")

        assertNotNull(error, "an Int expression cannot be bound to a String Context")
        assertContains(error, "Test Value holds String")
        assertContains(error, "Int cannot be bound to")
    }


    @Test
    fun aBindWhoseExpressionCanBeNullIsRejectedAgainstANonNullableContext() {
        // The nullability half survives the registry-visibility approximation that sends an unnameable
        // classifier to `Any`, so it is checked even where the class comparison is skipped.
        val error = errorOf("test/script/context/script-context-bind-nullable-test.yaml", "main.steps/Bind")

        assertNotNull(error)
        assertContains(error, "which is not nullable")
        assertContains(error, "this expression can evaluate to null")
    }


    @Test
    fun anUnconfiguredBindStepDefinesAndAsksForAContext() {
        assertDefines("main.steps/Bind")

        assertContains(
            assertNotNull(errorOf(unconfiguredPath, "main.steps/Bind")),
            "No context to bind into")
    }


    /**
     * The `uses` half of the same contract — and the one that FAILS today, because attribute metadata and
     * attribute values inherit in opposite directions.
     *
     * `GraphNotation.firstAttribute` takes the first hit on the most-derived-first inheritance chain, so
     * attribute VALUES are closest-wins. `NotationMetadataReader.readObjectImpl` walks that same chain
     * assigning into one map, so the last writer — the most DISTANT ancestor — wins for attribute METADATA.
     * A subtype therefore cannot refine an inherited attribute's `meta:` at all, and `UseContextStep`'s
     * narrowing of `uses` to a single nullable reference never takes effect: `WeakAttributeDefiner` sees
     * `ScriptStep`'s non-nullable `is: List`, against which the archetype's `uses: ""` default is an empty
     * object reference. The step is dropped from the graph rather than validated, so a palette-inserted Use
     * step cannot exist.
     *
     * `BindStep` and `ReleaseStep` escape it only because what they restate is value-identical to their bases;
     * the `editor: SelectContextEditor` key all three add is discarded the same way.
     */
    @Test
    fun anUnconfiguredUseContextStepDefinesAndAsksForAContext() {
        assertDefines("main.steps/Read")

        assertContains(
            assertNotNull(errorOf(unconfiguredPath, "main.steps/Read")),
            "No context to read")
    }


    @Test
    fun aDeclarationNamingNothingIsReportedAsABrokenName() {
        val error = errorOf(unconfiguredPath, "main.steps/Read Dangling")

        assertNotNull(error)
        assertContains(error, "Not a context: NoSuchContext")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val graphNotation: GraphNotation by lazy {
        AutoTestUtils.readNotation()
    }


    private val unconfiguredPath =
        DocumentPath.parse("test/script/context/script-context-generic-unconfigured-test.yaml")


    // An undefined step is invisible to the validator (it reports "Not found" instead of the step's own
    // verdict), so the message assertions below are only meaningful once this holds.
    private fun assertDefines(stepObjectPath: String) {
        val step = unconfiguredPath.toObjectLocation(ObjectPath.parse(stepObjectPath))
        val attempt = AutoTestUtils.graphDefinitionAttempt(graphNotation)

        assertTrue(step in attempt.transitiveSuccessful.objectDefinitions,
            "the empty-string declaration default must DEFINE, or the step is not in the graph at all: " +
                    "$step - ${attempt.failures[step]}")
    }


    private fun errorOf(documentPathString: String, stepObjectPath: String): String? {
        return errorOf(DocumentPath.parse(documentPathString), stepObjectPath)
    }


    private fun errorOf(documentPath: DocumentPath, stepObjectPath: String): String? {
        return scriptValidationFor(documentPath)
            .stepValidations[ObjectPath.parse(stepObjectPath)]
            ?.errorMessage
    }


    private fun scriptValidationFor(documentPath: DocumentPath): ScriptValidation {
        val graphDefinitionAttempt = AutoTestUtils.graphDefinitionAttempt(graphNotation)

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
