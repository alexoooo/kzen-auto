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
 * provides, requires or releases, and the Script spine acts on those declarations uniformly.
 *
 * Each test below pins one decision that is easy to get subtly wrong, and several of them pin a case an
 * earlier design draft got wrong outright:
 *
 * - [unmetRequiresFailsAtTheSpineNotAtTheRead] — the gate fires before the step body, with one framing,
 *   instead of at whatever ad-hoc read the step happens to reach first.
 * - [closerWithNothingOpenSucceeds] / [productionBrowserCloseWithNoBrowserSucceeds] — a step declaring
 *   `releases:` is NEVER gated and tolerates absence. Declaring closers as `requires:` (the earlier draft)
 *   would turn their deliberate "nothing to close" branch into a hard failure plus a pause-on-error park.
 * - [providerReadsBackItsOwnProvidesArgumentFree] — an argument-free read resolves against `provides` ∪
 *   `requires` ∪ `releases`, not `requires` alone: a provider declares no `requires` (the gate would fail it
 *   before it could provide), yet its replace-existing path must still read.
 * - [qualifiedMembersOfOneFamilyAreIndependent] — `sut:a` and `sut:b` are separate registrations under one
 *   family slot.
 * - [typedProvideInsideHostedDocumentReachesTheCallersSlot] — the typed API resolves through the engine's
 *   slot ownership exactly as the raw string API does.
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
    fun unmetRequiresFailsAtTheSpineNotAtTheRead() {
        val outcome = runScript("test/script-context-requires-unmet-test.yaml")

        val failed = assertIs<Outcome.Failed>(outcome)
        assertContains(failed.message, "Requires Test SUT: not provided")
        assertEquals(listOf(), ContextProbeLog.entries(),
            "the gate fires BEFORE the step body — the step never ran")
    }


    @Test
    fun closerWithNothingOpenSucceeds() {
        val outcome = runScript("test/script-context-release-tolerant-test.yaml")

        assertIs<Outcome.Success>(outcome)
        assertEquals(listOf("release saw nothing"), ContextProbeLog.entries(),
            "a `releases:` step is not gated: its body runs and tolerates the absence")
    }


    @Test
    fun productionBrowserCloseWithNoBrowserSucceeds() {
        // The real BrowserCloseStep archetype, not a test stand-in: it declares `releases: BrowserContext`,
        // so closing a browser that was never opened is success, exactly as before the feature.
        val outcome = runScript("test/script-context-browser-close-tolerant-test.yaml")
        assertIs<Outcome.Success>(outcome, (outcome as? Outcome.Failed)?.message)
    }


    @Test
    fun providerReadsBackItsOwnProvidesArgumentFree() {
        val outcome = runScript("test/script-context-replace-test.yaml")

        assertIs<Outcome.Success>(outcome)
        assertEquals(
            listOf(
                "provide[first] saw nothing",
                "provide[second] saw first",
                // Supersession, from the typed side: re-providing a live key disposes the registration it
                // displaces, right there rather than never (logic-spec §6). A provider that means to replace
                // an existing resource should still tear it down and `releaseContext` it FIRST — as
                // BrowserOpenStep does — because this closer runs after the replacement is already registered.
                "disposed[first]",
                "require saw second",
                "disposed[second]"),
            ContextProbeLog.entries(),
            "a provider resolves its replace-existing read off `provides`, having no `requires` to infer from")
    }


    @Test
    fun qualifiedMembersOfOneFamilyAreIndependent() {
        val outcome = runScript("test/script-context-qualified-test.yaml")

        assertIs<Outcome.Success>(outcome)
        assertEquals(
            listOf(
                "provide[alpha] saw nothing",
                "provide[beta] saw nothing",
                "release saw alpha",
                "release saw beta"),
            ContextProbeLog.entries(),
            "`sut:a` and `sut:b` are independent registrations under one family slot")
    }


    @Test
    fun typedProvideInsideHostedDocumentReachesTheCallersSlot() {
        // The caller declares the slot; the hosted sub-Script provides and declares none, so ownership walks
        // up. Without the slot the provide would bind to the sub-Script and die at its settle, and the
        // caller's Read would be gated out — so the Success here IS the assertion that ownership crossed.
        val outcome = runScript("test/script-context-host-test.yaml")

        assertIs<Outcome.Success>(outcome)
        assertEquals(
            listOf(
                "provide[hosted] saw nothing",
                "require saw hosted",
                "disposed[hosted]"),
            ContextProbeLog.entries(),
            "a typed provide inside a hosted document survives that document's settle when a caller owns it")
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
