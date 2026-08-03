package tech.kzen.auto.server.exec.script

import tech.kzen.auto.common.objects.document.logic.context.LogicContextAnalysis
import tech.kzen.auto.common.objects.document.logic.context.LogicContextFindings
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
 * The editor-side half of the context feature (logic-spec §6): which steps ask for a run-scoped Context nothing
 * upstream binds, plus the neighbouring authoring mistakes the same notation walk can see.
 *
 * Severity is the feature's stance, so every test below asserts which channel a finding lands in. An
 * unsatisfied requirement is an ERROR that disables Run: availability is decidable from declarations alone, so
 * a requirement nothing satisfies could never have succeeded. The converse gets nothing at all — a resource
 * provided and never consumed is a legitimate pattern, and keeping one private is the default. What remains is
 * advisory: a dangling reference, a shared resource key, an export nothing can back, a retired `context.slots`.
 *
 * Four of these fixtures pin decisions that are easy to get subtly wrong, so none is optional:
 * [anUnexportedHostedBindIsSilentAndItsConsumerErrors] (the soundness fix — the analysis must agree with the
 * engine's export chain or it certifies exactly the configuration that fails at run time),
 * [manualBindReachesTheCallerWithoutAnExport] (the escape hatch orthogonal to the chain, which the
 * FormulaError self-test ships), [consumerAfterAReleaseErrors] (what the third marker buys), and
 * [hostedRequiresErrorsOnTheCallersRunStep] (where a deleted export surfaces).
 */
class ScriptContextValidationTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun unsatisfiedUsesErrors() {
        val findings = analyze("test/script/context/script-context-unsatisfied-test.yaml")

        assertEquals(setOf(ObjectPath.parse("main.steps/Read")), findings.errors.keys)
        assertContains(findings.errors.getValue(ObjectPath.parse("main.steps/Read")), "Uses Test SUT")
    }


    @Test
    fun documentRequiresSeedsAvailabilityAndSilencesItsOwnSteps() {
        // The author asserting "a caller provides this" is the legitimate escape hatch, and it is why a
        // requiring sub-script is clean in its own document view.
        assertEquals(LogicContextFindings.empty, analyze("test/script/context/script-context-satisfied-test.yaml"))
    }


    @Test
    fun anUnexportedHostedBindIsSilentAndItsConsumerErrors() {
        val findings = analyze("test/script/context/script-context-private-provide-test.yaml")
        val runPath = ObjectPath.parse("main.steps/Run")

        assertTrue(runPath !in findings.errors && runPath !in findings.warnings,
            "a private bind is the default and usually the intent, so the RunStep reports nothing")

        val readError = findings.errors[ObjectPath.parse("main.steps/Read")]
        assertTrue(readError != null,
            "an unexported bind dies at its own document's settle, so it is not available here")
        assertContains(readError, "script-context-private-child-test.yaml binds it but does not export it")
    }


    @Test
    fun aHostedDocumentsExportSatisfiesItsCaller() {
        // The inverse of the fixture above, differing only by the callee's `context.exports` declaration — which
        // both makes the Context available here and hands this frame the ownership that keeps it alive.
        assertEquals(LogicContextFindings.empty, analyze("test/script/context/script-context-host-test.yaml"))
    }


    @Test
    fun anExportIsAvailableAfterItsRunStepAndNotBefore() {
        val findings = analyze("test/script/context/script-context-export-positional-test.yaml")

        assertEquals(setOf(ObjectPath.parse("main.steps/Read Before")), findings.errors.keys,
            "availability accumulates in document order — the same consumer is satisfied below the call")
    }


    @Test
    fun anExportChainCarriesAContextAcrossTwoDocuments() {
        assertEquals(LogicContextFindings.empty, analyze("test/script/context/script-context-export-chain-test.yaml"),
            "the root receives what the leaf offers, because every frame in between re-exports it")
        assertEquals(LogicContextFindings.empty, analyze("test/script/context/script-context-export-chain-mid-test.yaml"),
            "the middle document's export is backed by the leaf it runs, not only by a step of its own")
    }


    @Test
    fun anExportNothingInTheDocumentCanProvideWarns() {
        val findings = analyze("test/script/context/script-context-unbacked-export-test.yaml")

        assertEquals(mapOf(), findings.errors, "an unkeepable promise breaks nobody's run")
        val warning = findings.warnings[ObjectPath.parse("main")]
        assertTrue(warning != null, "an export the document cannot deliver is reported nowhere else")
        assertContains(warning, "Exports Test SUT, which nothing in this document can provide")
    }


    @Test
    fun legacyContextSlotsIsDeprecatedAndOwnsNothing() {
        val findings = analyze("test/script/context/script-context-legacy-slots-test.yaml")

        val warning = findings.warnings[ObjectPath.parse("main")]
        assertTrue(warning != null, "an inert declaration must be visible rather than silently ignored")
        assertContains(warning, "Context slots has no effect")

        val readError = findings.errors[ObjectPath.parse("main.steps/Read")]
        assertTrue(readError != null,
            "the retired key captures nothing — the hosted bind stays private to the document that made it")
        assertContains(readError, "does not export it")
    }


    @Test
    fun manualBindReachesTheCallerWithoutAnExport() {
        // Structurally identical to the unexported case — nothing exported anywhere — but the hosted bind is
        // `manual`, so the engine's hand-up carries it to this document at the hosted one's settle.
        assertEquals(LogicContextFindings.empty, analyze("test/script/context/script-context-manual-escape-test.yaml"))
    }


    @Test
    fun manualReachIsModelledOneLevelDeepOnly() {
        // The hand-up repeats at every settle, so this resource does arrive at run time; the analysis reads only
        // the document each RunStep names, so two levels down it errors. The remedy is the export chain, which
        // is the declaration this shape wants anyway.
        val findings = analyze("test/script/context/script-context-manual-two-level-test.yaml")

        val readError = findings.errors[ObjectPath.parse("main.steps/Read")]
        assertTrue(readError != null, "a Manual bind two documents down is not modelled")
        assertContains(readError, "Uses Test SUT")
        assertTrue("does not export it" !in readError,
            "the binder is two documents down, so the one-level scan cannot name it")
    }


    @Test
    fun hostedRequiresErrorsOnTheCallersRunStep() {
        val findings = analyze("test/script/context/script-context-hosted-requires-test.yaml")

        val runError = findings.errors[ObjectPath.parse("main.steps/Run")]
        assertTrue(runError != null, "the callee's unmet requirement must surface at the call site")
        assertContains(runError, "requires Test SUT")
    }


    @Test
    fun consumerAfterAReleaseErrors() {
        val findings = analyze("test/script/context/script-context-after-release-test.yaml")

        assertEquals(setOf(ObjectPath.parse("main.steps/Read After")), findings.errors.keys,
            "only the consumer placed after the closer errors — the one before it is satisfied")
    }


    @Test
    fun danglingContextReferenceWarns() {
        val findings = analyze("test/script/context/script-context-dangling-test.yaml")

        val warning = findings.warnings[ObjectPath.parse("main.steps/Read")]
        assertTrue(warning != null, "a declaration naming nothing is reported nowhere else")
        assertContains(warning, "'NoSuchContext', which is not a context")
    }


    @Test
    fun danglingUsesDoesNotFailTheStepsDefinition() {
        // The declarations are weak references (by: Nominal): a dangling entry warns (above), but must never
        // prune the step — WeakAttributeDefiner emits the reference without resolving it, and weak edges are
        // invisible to transitiveSuccessful. A dangling entry is an authoring mistake, not a broken document.
        val attempt = AutoTestUtils.graphDefinitionAttempt(graphNotation)

        val step = ObjectLocation(
            DocumentPath.parse("test/script/context/script-context-dangling-test.yaml"),
            ObjectPath.parse("main.steps/Read"))

        assertTrue(attempt.failures[step] == null,
            "dangling weak reference must not fail definition")
        assertTrue(step in attempt.transitiveSuccessful.objectDefinitions,
            "dangling weak reference must not get the step pruned")
    }


    @Test
    fun contextsSharingOneExactAddressAreReportedGraphWide() {
        // Grouped by the DERIVED exact address, so two declarations sharing a family with different declared
        // qualifiers stay silent — that shape is how two databases are meant to be written.
        val findings = analyze("test/script/context/script-context-alias-test.yaml")

        val warning = findings.warnings[ObjectPath.parse("main.steps/Read")]
        assertTrue(warning != null, "two Contexts resolving to one address are one registration at run time")
        assertContains(warning, "shares the address 'probe'")
        assertContains(warning, "Probe Two")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val graphNotation: GraphNotation by lazy {
        AutoTestUtils.readNotation()
    }


    private fun analyze(documentPathString: String): LogicContextFindings {
        return LogicContextAnalysis.analyze(graphNotation, DocumentPath.parse(documentPathString))
    }
}
