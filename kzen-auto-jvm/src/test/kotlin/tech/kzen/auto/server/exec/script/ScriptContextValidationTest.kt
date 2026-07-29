package tech.kzen.auto.server.exec.script

import tech.kzen.auto.common.objects.document.logic.context.LogicContextAnalysis
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue


/**
 * The editor-side half of the context feature (logic-spec §6): which steps ask for a run-scoped Context
 * nothing upstream provides, plus the three neighbouring authoring mistakes the same notation walk can see.
 *
 * Everything asserted here is a WARNING. That distinction is the feature's stance and worth restating: a
 * document whose requirement nothing local satisfies may be entirely correct once a caller provides it, and
 * the editor cannot see the caller — so it flags rather than refuses, and Run is never blocked.
 *
 * Four of these fixtures pin corrections that an earlier draft of the design got wrong, so none is optional:
 * [unslottedCrossDocumentProvideWarnsAndIsNotAvailable] (the soundness fix — the analysis must agree with the
 * engine's Self fallback or it certifies exactly the configuration that fails at run time),
 * [manualProvideEscapesWithoutASlot] (the second escape mechanism, which the FormulaError self-test ships),
 * [consumerAfterAReleaseWarns] (what the third marker buys), and [hostedRequiresWarnsOnTheCallersRunStep]
 * (where a deleted slot actually surfaces).
 */
class ScriptContextValidationTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun unsatisfiedRequiresWarns() {
        val warnings = analyze("test/script-context-warn-unsatisfied-test.yaml")

        assertEquals(setOf(ObjectPath.parse("main.steps/Read")), warnings.keys)
        assertContains(warnings.getValue(ObjectPath.parse("main.steps/Read")), "Requires Test SUT")
    }


    @Test
    fun documentRequiresSeedsAvailabilityAndSilencesItsOwnSteps() {
        // The author asserting "a caller provides this" is the legitimate escape hatch, and it is why a
        // requiring sub-script never ambers in its own document view.
        assertEquals(mapOf(), analyze("test/script-context-warn-satisfied-test.yaml"))
    }


    @Test
    fun unslottedCrossDocumentProvideWarnsAndIsNotAvailable() {
        val warnings = analyze("test/script-context-warn-unslotted-provide-test.yaml")

        val runWarning = warnings[ObjectPath.parse("main.steps/Run")]
        assertTrue(runWarning != null, "the escaping provide must be reported on the RunStep")
        assertContains(runWarning, "no enclosing slot owns it")

        assertTrue(warnings[ObjectPath.parse("main.steps/Read")] != null,
            "the hosted provide must NOT count as available — it dies at the hosted document's settle")
    }


    @Test
    fun aCallerDeclaredSlotSilencesBothWarnings() {
        // The inverse of the fixture above, differing only by the caller's `context.slots` declaration.
        assertEquals(mapOf(), analyze("test/script-context-host-test.yaml"))
    }


    @Test
    fun manualProvideEscapesWithoutASlot() {
        // Structurally identical to the unslotted case — no slot anywhere — but the hosted provide is
        // `manual`, so the engine's hand-up carries it to this document at the hosted one's settle.
        assertEquals(mapOf(), analyze("test/script-context-warn-manual-escape-test.yaml"))
    }


    @Test
    fun hostedRequiresWarnsOnTheCallersRunStep() {
        val warnings = analyze("test/script-context-warn-hosted-requires-test.yaml")

        val runWarning = warnings[ObjectPath.parse("main.steps/Run")]
        assertTrue(runWarning != null, "the callee's unmet requirement must surface at the call site")
        assertContains(runWarning, "requires Test SUT")
    }


    @Test
    fun consumerAfterAReleaseWarns() {
        val warnings = analyze("test/script-context-warn-after-release-test.yaml")

        assertEquals(setOf(ObjectPath.parse("main.steps/Read After")), warnings.keys,
            "only the consumer placed after the closer warns — the one before it is satisfied")
    }


    @Test
    fun danglingContextReferenceWarns() {
        val warnings = analyze("test/script-context-warn-dangling-test.yaml")

        val warning = warnings[ObjectPath.parse("main.steps/Read")]
        assertTrue(warning != null, "a declaration naming nothing is reported nowhere else")
        assertContains(warning, "'NoSuchContext', which is not a context")
    }


    @Test
    fun danglingRequiresDoesNotFailTheStepsDefinition() {
        // The declarations are weak references (by: Nominal): a dangling entry warns (above), but must never
        // prune the step — WeakAttributeDefiner emits the reference without resolving it, and weak edges are
        // invisible to transitiveSuccessful. This is the definition-layer half of the "warn, never block" stance.
        val attempt = AutoTestUtils.graphDefinitionAttempt(graphNotation)

        val step = ObjectLocation(
            DocumentPath.parse("test/script-context-warn-dangling-test.yaml"),
            ObjectPath.parse("main.steps/Read"))

        assertTrue(attempt.failures[step] == null,
            "dangling weak reference must not fail definition")
        assertTrue(step in attempt.transitiveSuccessful.objectDefinitions,
            "dangling weak reference must not get the step pruned")
    }


    @Test
    fun contextsSharingOneKeyAreReportedGraphWide() {
        val warnings = analyze("test/script-context-warn-alias-test.yaml")

        val warning = warnings[ObjectPath.parse("main.steps/Read")]
        assertTrue(warning != null, "two Contexts naming one key are one registration at run time")
        assertContains(warning, "shares the resource key 'probe'")
        assertContains(warning, "Probe Two")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val graphNotation: GraphNotation by lazy {
        AutoTestUtils.readNotation()
    }


    private fun analyze(documentPathString: String): Map<ObjectPath, String> {
        return LogicContextAnalysis.analyze(graphNotation, DocumentPath.parse(documentPathString))
    }
}
