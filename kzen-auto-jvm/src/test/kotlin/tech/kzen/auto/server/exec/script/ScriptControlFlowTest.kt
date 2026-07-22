package tech.kzen.auto.server.exec.script

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.exec.script.test.CountingStep
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
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue


/**
 * End-to-end control-flow semantics (execution-control phase XC4): Skip Iteration (continue) / Finish Loop
 * (break) via ControlStep, and ResultStep `then: endScript` (return) — modelled as completion signals, run on
 * the real [RunEngine]. Each Script drives the production control steps plus the test-only [CountingStep] to
 * make execution observable. The counter is process-global, so tests reset it per run and rely on the suite's
 * sequential execution (as the other static-fixture engine tests do).
 */
class ScriptControlFlowTest {
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
    fun skipIterationInForEachContributesNothingAndContinues() {
        // The signal is raised under an If (verify case 5 — it passes through the container), the Item == 2
        // iteration collects nothing, and iterations 1 and 3 run their Tail (CountingStep). Result = [1, 2].sum().
        val outcome = runScript("test/script-control-foreach-skip-test.yaml")
        assertEquals(3, assertIs<Outcome.Success>(outcome).value.mainComponentValue())
        assertEquals(2, CountingStep.count.get())
    }


    @Test
    fun finishLoopInForEachExitsWithOutputsSoFarAndContinuesAfterTheLoop() {
        val outcome = runScript("test/script-control-foreach-finish-test.yaml")
        // Loop.sum() = [1, 2].sum() = 3 (Finish at Item == 3), and the post-loop After (10) still runs.
        assertEquals(13, assertIs<Outcome.Success>(outcome).value.mainComponentValue())
    }


    @Test
    fun controlStepTargetingOuterLoopUnwindsInnerAndOuterConsumes() {
        val outcome = runScript("test/script-control-nested-finish-outer-test.yaml")
        assertIs<Outcome.Success>(outcome)
        // Only the outer's first iteration completes its inner loop (2 iterations); the outer's second iteration
        // finishes the OUTER loop from the inner body before Tail runs.
        assertEquals(2, CountingStep.count.get())
    }


    @Test
    fun skipIterationInDoWhileProceedsToCondition() {
        val outcome = runScript("test/script-control-dowhile-skip-test.yaml")
        assertIs<Outcome.Success>(outcome)
        // The first iteration skips (after Counter ran), then the condition evaluates and the loop runs while
        // Counter < 3 — so CountingStep runs exactly 3 times.
        assertEquals(3, CountingStep.count.get())
    }


    @Test
    fun skipInDoWhileLeavingAConditionValueUnproducedFailsViaTheBackstop() {
        // The skip short-circuits before Later runs; the condition references Later, hitting the existing
        // "No value produced" backstop => the run fails.
        val outcome = runScript("test/script-control-dowhile-backstop-test.yaml")
        assertIs<Outcome.Failed>(outcome)
    }


    @Test
    fun endScriptAtRootEndsTheRunAndLaterStepsNeverRun() {
        val outcome = runScript("test/script-control-endscript-test.yaml")
        assertEquals(1, assertIs<Outcome.Success>(outcome).value.mainComponentValue())
        assertEquals(0, CountingStep.count.get())
    }


    @Test
    fun endScriptInAHostedSubScriptReturnsAndTheCallerContinues() {
        // The child ends itself with `then: endScript` (returns 5); its ChildTail never runs (count stays 0),
        // and the caller continues past the RunStep to compute Call + 1 = 6 — proving End Script never crosses
        // the host() boundary.
        val outcome = runScript("test/script-control-endscript-parent-test.yaml")
        assertEquals(6, assertIs<Outcome.Success>(outcome).value.mainComponentValue())
        assertEquals(0, CountingStep.count.get())
    }


    @Test
    fun mistargetedControlStepDoesNotRunSuccessfully() {
        // A ControlStep targeting a non-enclosing loop is a validation error (Run-blocking); at runtime the
        // signal reaches the root unconsumed and the backstop fails the run. Either rejection is acceptable.
        val result = runCatching { runScript("test/script-control-root-backstop-test.yaml") }
        val outcome = result.getOrNull()
        assertTrue(
            result.isFailure || outcome is Outcome.Failed,
            "expected a mistargeted ControlStep to be rejected, got $outcome")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun runScript(documentPathString: String, inputs: TupleValue = TupleValue.empty): Outcome {
        ScriptStepTestModule.register()
        CountingStep.reset()

        context = KzenAutoContext.forTest()

        val documentPath = DocumentPath.parse(documentPathString)
        val scriptLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))

        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinition = AutoTestUtils.graphDefinitionAttempt(graphNotation).transitiveSuccessful

        val logic = ScriptLogicCompiler.compile(
            scriptLocation,
            graphNotation,
            graphDefinition,
            compilerServices())

        val engine = RunEngine(logic, context.objectStableMapper.objectStableId(scriptLocation), inputs)
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
