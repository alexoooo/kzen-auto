package tech.kzen.auto.server.exec.script

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompiler
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.exec.script.test.ContextProbeLog
import tech.kzen.auto.server.exec.script.test.ScriptStepTestModule
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.server.exec.engine.RunEngine
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs


/**
 * The runtime half of the context feature (logic-spec §6): a step DECLARES the run-scoped resources it
 * binds, uses or releases, and the Script spine acts on those declarations uniformly.
 *
 * Each test below pins one decision that is easy to get subtly wrong, and several of them pin a case an
 * earlier design draft got wrong outright:
 *
 * - [unmetUsesFailsAtTheSpineNotAtTheRead] — the gate fires before the step body, with one framing,
 *   instead of at whatever ad-hoc read the step happens to reach first.
 * - [closerWithNothingOpenSucceeds] / [productionBrowserCloseWithNoBrowserSucceeds] — a step declaring
 *   `releases:` is NEVER gated and tolerates absence. Declaring closers as `uses:` (the earlier draft)
 *   would turn their deliberate "nothing to close" branch into a hard failure plus a pause-on-error park.
 * - [binderReadsBackItsOwnBindsArgumentFree] — an argument-free read resolves against `binds` ∪
 *   `uses` ∪ `releases`, not `uses` alone: a binder declares no `uses` (the gate would fail it
 *   before it could bind), yet its replace-existing path must still read.
 * - [qualifiedMembersOfOneFamilyAreIndependent] — `sut:a` and `sut:b` are separate registrations within one
 *   Context family.
 * - [typedBindExportedByAHostedDocumentReachesTheCaller] /
 *   [anUnexportedBindIsDisposedAtItsBindersSettle] — the two halves of the export chain, on documents
 *   that differ by nothing but the callee's `context.exports`: an exported bind climbs to the caller, an
 *   unexported one is private to the document that made it. The typed API resolves ownership exactly as the
 *   raw string API does.
 * - [documentRequiresGateFailsTheRunBeforeAnyStep] — a document declaring `context.requires` asserts a caller
 *   that supplies it, so running it directly fails at run start rather than at its first requiring step.
 *
 * Shares the process-global [ContextProbeLog] and resets it per run, so it relies on the suite's sequential
 * execution (as the other static-fixture engine tests do).
 */
class ScriptContextRuntimeTest {
    //-----------------------------------------------------------------------------------------------------------------
    private lateinit var context: KzenAutoContext


    @AfterTest
    fun tearDown() {
        if (::context.isInitialized) {
            context.close()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun unmetUsesFailsAtTheSpineNotAtTheRead() {
        val outcome = runScript("test/script/context/script-context-requires-unmet-test.yaml")

        val failed = assertIs<Outcome.Failed>(outcome)
        assertContains(failed.message, "Uses Test SUT: not bound")
        assertEquals(listOf(), ContextProbeLog.entries(),
            "the gate fires BEFORE the step body — the step never ran")
    }


    @Test
    fun closerWithNothingOpenSucceeds() {
        val outcome = runScript("test/script/context/script-context-release-tolerant-test.yaml")

        assertIs<Outcome.Success>(outcome)
        assertEquals(listOf("release saw nothing"), ContextProbeLog.entries(),
            "a `releases:` step is not gated: its body runs and tolerates the absence")
    }


    @Test
    fun productionBrowserCloseWithNoBrowserSucceeds() {
        // The real BrowserCloseStep archetype, not a test stand-in: it declares `releases: BrowserContext`,
        // so closing a browser that was never opened is success, exactly as before the feature.
        val outcome = runScript("test/script/context/script-context-browser-close-tolerant-test.yaml")
        assertIs<Outcome.Success>(outcome, (outcome as? Outcome.Failed)?.message)
    }


    @Test
    fun binderReadsBackItsOwnBindsArgumentFree() {
        val outcome = runScript("test/script/context/script-context-replace-test.yaml")

        assertIs<Outcome.Success>(outcome)
        assertEquals(
            listOf(
                "provide[first] saw nothing",
                "provide[second] saw first",
                // Supersession, from the typed side: re-binding a live key disposes the registration it
                // displaces, right there rather than never (logic-spec §6). A binder that means to replace
                // an existing resource should `releaseContext` it FIRST — as BrowserOpenStep does — because
                // this closer otherwise runs after the replacement is already registered. It does NOT tear the
                // resource down itself: release runs the disposal the prior binder attached, exactly once.
                "disposed[first]",
                "require saw second",
                "disposed[second]"),
            ContextProbeLog.entries(),
            "a binder resolves its replace-existing read off `binds`, having no `uses` to infer from")
    }


    @Test
    fun qualifiedMembersOfOneFamilyAreIndependent() {
        val outcome = runScript("test/script/context/script-context-qualified-test.yaml")

        assertIs<Outcome.Success>(outcome)
        assertEquals(
            listOf(
                "provide[alpha] saw nothing",
                "provide[beta] saw nothing",
                // Each `disposed[…]` precedes its own `release saw …` because releasing a binding runs the
                // disposal the binder attached, and the step records what it saw only afterwards. The
                // interleaving is the assertion: alpha's disposal fires on alpha's release, not beta's, so
                // one member of the family cannot tear down its sibling.
                "disposed[alpha]",
                "release saw alpha",
                "disposed[beta]",
                "release saw beta"),
            ContextProbeLog.entries(),
            "`sut:a` and `sut:b` are independent registrations within one Context family")
    }


    @Test
    fun typedBindExportedByAHostedDocumentReachesTheCaller() {
        // The hosted sub-Script binds and EXPORTS; the caller declares nothing, which is how it receives —
        // it is the first frame that does not export the Context, so ownership rests there. The caller's Read
        // runs after that sub-Script settled, so the Success here IS the assertion that ownership climbed.
        val outcome = runScript("test/script/context/script-context-host-test.yaml")

        assertIs<Outcome.Success>(outcome)
        assertEquals(
            listOf(
                "provide[hosted] saw nothing",
                "require saw hosted",
                "disposed[hosted]"),
            ContextProbeLog.entries(),
            "an exported bind outlives the settle of the document that made it, and disposes at its owner's")
    }


    @Test
    fun anUnexportedBindIsDisposedAtItsBindersSettle() {
        val outcome = runScript("test/script/context/script-context-private-provide-test.yaml")

        val failed = assertIs<Outcome.Failed>(outcome)
        assertContains(failed.message, "Uses Test SUT: not bound")
        assertEquals(
            listOf(
                "provide[private] saw nothing",
                "disposed[private]"),
            ContextProbeLog.entries(),
            "a bind nothing exports is private to its own frame — the caller's consumer is gated out")
    }


    @Test
    fun documentRequiresGateFailsTheRunBeforeAnyStep() {
        val outcome = runScript("test/script/context/script-context-satisfied-test.yaml")

        val failed = assertIs<Outcome.Failed>(outcome)
        assertContains(failed.message, "Requires Test SUT: not provided by caller")
        assertEquals(listOf(), ContextProbeLog.entries(),
            "a document asserting a caller cannot run standalone, and says so at run start")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun runScript(documentPathString: String): Outcome {
        ScriptStepTestModule.register()
        ContextProbeLog.reset()

        context = KzenAutoContext.forTest()

        val documentPath = DocumentPath.parse(documentPathString)
        val scriptLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))

        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinition = AutoTestUtils.graphDefinitionAttempt(graphNotation).transitiveSuccessful

        val logic = LogicCompiler.compile(
            scriptLocation,
            graphNotation,
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
