package tech.kzen.auto.server.exec.script

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.objects.document.logic.context.LogicContextAnalysis
import tech.kzen.auto.common.objects.document.logic.context.LogicContextFindings
import tech.kzen.auto.common.objects.document.script.model.ScriptValidation
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompiler
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.exec.script.test.ContextProbeLog
import tech.kzen.auto.server.exec.script.test.ScriptStepTestModule
import tech.kzen.auto.server.objects.script.ScriptValidator
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.server.exec.engine.RunEngine
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


/**
 * Call-site context binding (CX7b): a RunStep's `contexts:` map, which supplies the document it runs with a
 * Context the caller holds, for that call only.
 *
 * This is the one thing no amount of qualifier plumbing reaches. A qualifier would have to be threaded through
 * every step of the callee; a call-site binding leaves the callee **unedited and unaware** — it declares
 * `context.requires` and is run twice against two different subjects. So the headline fixtures below share one
 * callee document between two calls and assert what each call's read actually saw.
 *
 * The fixtures are built so that a passing read can only come from the borrow:
 *
 * - [theSameUneditedCalleeRunsTwiceAgainstTwoSubjects] — the caller's registrations are `call-sut:a` and
 *   `call-sut:b` while the callee reads exact `call-sut`, so the ancestor walk answers nothing.
 * - [aBorrowBridgesTwoFamiliesThatShareNothing] — §4.7's asymmetry taken to its limit: source family
 *   `call-sut`, target family `call-driver`, no coincidence available at all.
 * - [aCalleeReleasingTheBorrowLeavesTheCallersOwnBindingIntact] — a borrow carries no disposal, so a release
 *   inside the callee unbinds a name and closes nothing; the same subject is lent again on the next call.
 * - [aMappedSourceNothingBoundFailsTheCallingRunStep] — attribution, which is half of why the map is worth
 *   having: the mistake is in the CALL and is reported there, not inside a callee that is not at fault.
 *
 * The remaining tests are the static half — the analysis crediting a mapping as a satisfaction source, and
 * RunStep's own declared-to-declared type check.
 *
 * Shares the process-global [ContextProbeLog] and resets it per run, so it relies on the suite's sequential
 * execution (as the other static-fixture engine tests do).
 */
class ScriptContextCallSiteTest {
    //-----------------------------------------------------------------------------------------------------------------
    private lateinit var context: KzenAutoContext


    @AfterTest
    fun tearDown() {
        if (::context.isInitialized) {
            context.close()
        }
    }


    //--------------------------------------------------------------------------------------------------------- runtime
    @Test
    fun theSameUneditedCalleeRunsTwiceAgainstTwoSubjects() {
        val outcome = runScript("test/script/context/script-context-call-two-subjects-test.yaml")

        assertIs<Outcome.Success>(outcome, (outcome as? Outcome.Failed)?.message)
        assertEquals(
            listOf(
                "provide[subject-a] saw nothing",
                "provide[subject-b] saw nothing",
                "require saw subject-a",
                "require saw subject-b"),
            ContextProbeLog.entries().filterNot { it.startsWith("disposed") },
            "one callee document, two calls, two subjects — and the callee names neither. Its read is exact " +
                    "on `call-sut`, which no caller registration answers to, so each value can only have " +
                    "arrived as that call's borrow")
    }


    @Test
    fun aBorrowBridgesTwoFamiliesThatShareNothing() {
        val outcome = runScript("test/script/context/script-context-call-asymmetric-test.yaml")

        assertIs<Outcome.Success>(outcome, (outcome as? Outcome.Failed)?.message)
        assertEquals(
            listOf("require saw driver-a", "require saw driver-b"),
            ContextProbeLog.entries().filter { it.startsWith("require") },
            "the source is read by the CALLER's key and installed under the CALLEE's, and here those keys " +
                    "share no family — so the map is the only thing that could have connected them")
    }


    @Test
    fun aCalleeReleasingTheBorrowLeavesTheCallersOwnBindingIntact() {
        val outcome = runScript("test/script/context/script-context-call-release-test.yaml")

        assertIs<Outcome.Success>(outcome, (outcome as? Outcome.Failed)?.message)
        assertEquals(
            listOf(
                "provide[lent] saw nothing",
                "require saw lent",
                "release saw lent",
                // The second call succeeds, so the first call's release removed the borrow and NOT the
                // caller's registration — and the single trailing disposal says the caller's own binding was
                // closed once, at its own frame's settle, rather than by its borrower.
                "require saw lent",
                "release saw lent",
                "disposed[lent]"),
            ContextProbeLog.entries(),
            "a borrow transfers no ownership: releasing it unbinds a name and closes nothing")
    }


    @Test
    fun aMappedSourceNothingBoundFailsTheCallingRunStep() {
        val outcome = runScript("test/script/context/script-context-call-missing-source-test.yaml")

        val failed = assertIs<Outcome.Failed>(outcome)
        assertContains(failed.message, "Callee SUT slot is mapped to Caller SUT A, which is not bound")
        assertEquals(listOf(), ContextProbeLog.entries(),
            "the call is refused before the child exists, so no step of the callee ran to be blamed")
    }


    //-------------------------------------------------------------------------------------------------------- analysis
    @Test
    fun aCallSiteBindingSatisfiesTheCalleesRequires() {
        assertEquals(
            LogicContextFindings.empty,
            analyze("test/script/context/script-context-call-two-subjects-test.yaml"),
            "without crediting the map, every parameterized call site would light up red for doing exactly " +
                    "what it was built for — the callee's requirement is met by a Context it never names")
    }


    @Test
    fun aMappedSourceNothingBindsErrorsOnTheRunStep() {
        val findings = analyze("test/script/context/script-context-call-missing-source-test.yaml")

        val runError = findings.errors[ObjectPath.parse("main.steps/Run")]
        assertNotNull(runError, "an unusable mapping is a mistake in the call, and is reported at the call")
        assertContains(runError, "requires Callee SUT slot, which this step maps to Caller SUT A")
        assertContains(runError, "nothing before this step binds that")
    }


    @Test
    fun aDanglingCalleeSlotWarnsAndSatisfiesNothing() {
        val findings = analyze("test/script/context/script-context-call-dangling-slot-test.yaml")

        val warning = findings.warnings[ObjectPath.parse("main.steps/Run")]
        assertNotNull(warning, "a map KEY is the one side a rename cannot rewrite, so this warning is the " +
                "only thing that names the string left behind")
        assertContains(warning, "supplies 'NoSuchContext', which is not a context")

        val runError = findings.errors[ObjectPath.parse("main.steps/Run")]
        assertNotNull(runError, "a mapping that resolves to nothing satisfies nothing")
        assertContains(runError, "requires Callee SUT slot")
    }


    //------------------------------------------------------------------------------------------------------ definition
    @Test
    fun aSourceTypeTheTargetCannotAcceptIsRejectedAtTheRunStep() {
        // No `Any` escape applies to either side, both being DECLARED types — the divergence from BindStep,
        // where `Any` is an inference approximation rather than a contract.
        val error = errorOf("test/script/context/script-context-call-type-mismatch-test.yaml", "main.steps/Run")

        assertNotNull(error, "an Int cannot be handed to a slot whose contract is String")
        assertContains(error, "Callee SUT slot holds String")
        assertContains(error, "Caller count's Int cannot be bound to")
    }


    @Test
    fun aRowWhoseSourceIsNotPickedYetStillDefines() {
        // Reachable, not hypothetical: a map entry is keyed by its target, so the editor materializes a row the
        // moment the SLOT is chosen and the source arrives a click later. Without `nullable: true` on the
        // value generic the weak definer answers "Empty object reference" for that instant and takes the whole
        // RunStep's definition with it — an error about notation the author is midway through writing.
        val step = DocumentPath
            .parse("test/script/context/script-context-call-half-written-test.yaml")
            .toObjectLocation(ObjectPath.parse("main.steps/Run"))

        val attempt = AutoTestUtils.graphDefinitionAttempt(graphNotation)

        assertTrue(step in attempt.transitiveSuccessful.objectDefinitions,
            "a half-written entry must define: ${attempt.failures[step]}")

        // And it satisfies nothing, which is the honest report rather than a second failure mode.
        val runError = analyze("test/script/context/script-context-call-half-written-test.yaml")
            .errors[ObjectPath.parse("main.steps/Run")]
        assertNotNull(runError)
        assertContains(runError, "requires Callee SUT slot, which nothing before this step binds")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val graphNotation: GraphNotation by lazy {
        AutoTestUtils.readNotation()
    }


    private fun analyze(documentPathString: String): LogicContextFindings {
        return LogicContextAnalysis.analyze(graphNotation, DocumentPath.parse(documentPathString))
    }


    private fun errorOf(documentPathString: String, stepObjectPath: String): String? {
        ScriptStepTestModule.register()
        context = KzenAutoContext.forTest()

        val documentPath = DocumentPath.parse(documentPathString)

        val stepGraphDefinition = AutoTestUtils
            .graphDefinitionAttempt(graphNotation)
            .transitiveSuccessful
            .filterTransitive(documentPath)

        val graphInstance = GraphCreator.createGraph(stepGraphDefinition, context.graphEnvironment)

        val validation: ScriptValidation = ScriptValidator.validate(
            documentPath, graphNotation, stepGraphDefinition, graphInstance, context.cachedKotlinCompiler)

        return validation.stepValidations[ObjectPath.parse(stepObjectPath)]?.errorMessage
    }


    private fun runScript(documentPathString: String): Outcome {
        ScriptStepTestModule.register()
        ContextProbeLog.reset()

        context = KzenAutoContext.forTest()

        val documentPath = DocumentPath.parse(documentPathString)
        val scriptLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))

        val runNotation = AutoTestUtils.readNotation()
        val graphDefinition = AutoTestUtils.graphDefinitionAttempt(runNotation).transitiveSuccessful

        val logic = LogicCompiler.compile(
            scriptLocation,
            runNotation,
            graphDefinition,
            compilerServices())

        val engine = RunEngine(logic, context.objectStableMapper.objectStableId(scriptLocation), TupleValue.empty)
        return try {
            runBlocking {
                engine.resume()
                engine.await()
            }
        }
        finally {
            engine.close()
        }
    }


    private fun compilerServices(): LogicCompilerServices {
        return LogicCompilerServices(
            context.graphEnvironment,
            context.objectStableMapper,
            context.cachedKotlinCompiler,
            context.scriptValidationCache,
            context.jobValidationCache,
            context.notationMetadataReader,
            context.jobWorkPool,
            LogicRunExecutionId.random())
    }
}
