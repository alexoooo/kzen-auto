package tech.kzen.auto.server.objects.script.step.context

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompiler
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.exec.script.test.ContextProbeLog
import tech.kzen.auto.server.exec.script.test.ScriptStepTestModule
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.auto.server.exec.mainBoundaryValue
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
 * The four FLAVOUR-NEUTRAL context steps driven end-to-end on the real engine: [BindStep], [UseContextStep],
 * [ReleaseStep], [DisposeAtSettleStep]. Where `ScriptContextRuntimeTest` pins the spine's uniform treatment of
 * context DECLARATIONS through hand-written test steps, these pin the shipped archetypes a user actually
 * inserts — every one of which reaches the runtime through notation the tests here are the first to
 * instantiate.
 *
 * Each test pins one decision the generic steps make that their browser-flavoured counterparts hide:
 *
 * - [aBoundValueReachesADownstreamExpressionAtItsDeclaredType] — the composition the whole feature exists for.
 *   The Formula concatenates the Use step's value, which compiles only against the Context's DECLARED type, so
 *   a Use publishing `Any` would fail the run rather than quietly widening it.
 * - [releasingADisposalFreeBindingOnlyRemovesTheName] — the half of [ReleaseStep] that has no browser analogue:
 *   with nothing attached there is nothing to tear down, yet the name must still go.
 * - [settleCleanupRunsAfterTheLastStepAndNeedsNoContext] — [DisposeAtSettleStep] registers at its own position
 *   and fires at the owning frame's settle, with no Context anywhere in the document.
 * - [settleCleanupDeclaredKeepOnFailureIsSkippedOnAFailedRun] — the second `SettleDisposalPolicy` spelling
 *   actually resolves through `SettleDisposalPolicyDefiner`.
 * - [releasingOneQualifiedMemberLeavesItsSiblingBound] — a computed qualifier addresses one member of a family
 *   through the generic steps, not only through a step written for one Context.
 *
 * Shares the process-global [ContextProbeLog] and resets it per run, so it relies on the suite's sequential
 * execution (as the other static-fixture engine tests do). A fixture makes itself observable by recording from
 * the user Kotlin expression the step under test compiles — the probe is on the test runtime classpath, which
 * is the same classloader the expression compiler builds its classpath from.
 */
class ContextStepRuntimeTest {
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
    fun aBoundValueReachesADownstreamExpressionAtItsDeclaredType() {
        val outcome = runScript("test/script/context/script-context-bind-use-test.yaml")

        assertEquals(
            "ambient value",
            assertIs<Outcome.Success>(outcome).value.mainBoundaryValue(),
            "Bind publishes into ambient scope, Use reads it into the value graph, and a Formula consumes " +
                    "that by reference — the three-step composition the generic steps exist to allow")
    }


    @Test
    fun releasingADisposalFreeBindingOnlyRemovesTheName() {
        val outcome = runScript("test/script/context/script-context-bind-release-plain-test.yaml")

        val failed = assertIs<Outcome.Failed>(outcome)
        assertContains(failed.message, "Uses Test Value: not bound",
            message = "the name is gone, so the consumer is gated out at the spine with the standard framing")

        assertEquals(listOf("bound"), ContextProbeLog.entries(),
            "a plain bind attaches no disposal, so the release runs none — contrast the managed binding of " +
                    "ScriptContextRuntimeTest.qualifiedMembersOfOneFamilyAreIndependent, where every release " +
                    "adds a disposal entry of its own")
    }


    @Test
    fun settleCleanupRunsAfterTheLastStepAndNeedsNoContext() {
        val outcome = runScript("test/script/context/script-context-dispose-at-settle-test.yaml")

        assertIs<Outcome.Success>(outcome)
        assertEquals(listOf("last step", "cleanup"), ContextProbeLog.entries(),
            "the cleanup is registered where the step sits but runs at the owning Script's settle, so a step " +
                    "placed after it still runs first")
    }


    @Test
    fun settleCleanupDeclaredKeepOnFailureIsSkippedOnAFailedRun() {
        val outcome = runScript("test/script/context/script-context-dispose-keep-on-failure-test.yaml")

        assertIs<Outcome.Failed>(outcome)
        assertEquals(listOf(), ContextProbeLog.entries(),
            "`keepOnFailure` reaches the step as the enum and leaves the side effect undone for inspection")
    }


    @Test
    fun releasingOneQualifiedMemberLeavesItsSiblingBound() {
        val outcome = runScript("test/script/context/script-context-bind-qualified-test.yaml")

        assertEquals(
            "beta",
            assertIs<Outcome.Success>(outcome).value.mainBoundaryValue(),
            "`a` and `b` are independent registrations within one family, addressed by the steps' own " +
                    "qualifier attribute rather than by the declaration's")
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

        val engine = RunEngine(logic, context.objectStableMapper.objectStableId(scriptLocation))
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
