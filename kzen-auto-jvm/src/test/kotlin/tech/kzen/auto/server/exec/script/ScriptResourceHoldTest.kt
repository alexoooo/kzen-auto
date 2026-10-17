package tech.kzen.auto.server.exec.script

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompiler
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.exec.mainBoundaryValue
import tech.kzen.auto.server.objects.job.worker.test.CloseCountingResource
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.server.exec.engine.RunEngine
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs


/**
 * Script ownership of what it records (resource-holds plan §3.4): a step's `AutoCloseable` value is held by the
 * Script frame through [ScriptRunContext.recordValue], so it is
 *
 * - live for a later step that reads it, and closed once at the Script's settle
 *   ([ownedStepValueIsLiveDownstreamAndClosesAtSettle]);
 * - closed when a loop body re-records its slot, each iteration superseding the previous iteration's value
 *   ([loopBodyValueClosesWhenSuperseded]);
 * - live in the caller when a sub-Script returns it, and closed with the caller rather than the child
 *   ([subScriptResultIsLiveInCallerAndClosesWithCaller]);
 * - carried across a live edit — the barrier never closes it, the resumed Script still reads it, and a hold
 *   carried into a resumed loop is still released on the next re-record ([ownedStepValueSurvivesLiveEdit],
 *   [carriedLoopHoldIsReleasedOnReRecordAfterLiveEdit]).
 *
 * Every step involved does nothing with the value but return it (no `openResource`, no context binding), so
 * what the tests observe is the recording alone. Each reader step throws if its value is already closed, so a
 * Success is the liveness proof and [CloseCountingResource]'s process-global counts are the closing proof;
 * like the other static-fixture engine tests this relies on the suite's sequential execution.
 */
class ScriptResourceHoldTest {
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
    fun ownedStepValueIsLiveDownstreamAndClosesAtSettle() {
        val outcome = runScript("test/script/resource/script-closeable-step-test.yaml")

        assertEquals("step-1", assertIs<Outcome.Success>(outcome).value.mainBoundaryValue())
        assertEquals(1, CloseCountingResource.opened.get())
        assertEquals(1, CloseCountingResource.closed.get(), "closed exactly once, at the Script's settle")
    }


    @Test
    fun loopBodyValueClosesWhenSuperseded() {
        val outcome = runScript("test/script/resource/script-closeable-loop-test.yaml")

        assertEquals("0,1,2", assertIs<Outcome.Success>(outcome).value.mainBoundaryValue(),
            "each iteration's Open closes the previous iteration's value, none its own")
        assertEquals(3, CloseCountingResource.opened.get())
        assertEquals(3, CloseCountingResource.closed.get(), "the last iteration's value closes at settle")
        assertEquals(listOf("loop-1", "loop-2", "loop-3"), CloseCountingResource.closeOrder)
    }


    @Test
    fun subScriptResultIsLiveInCallerAndClosesWithCaller() {
        val outcome = runScript("test/script/resource/script-closeable-parent-test.yaml")

        assertEquals("child-1", assertIs<Outcome.Success>(outcome).value.mainBoundaryValue(),
            "Check reads the child's result after the child has settled")
        assertEquals(1, CloseCountingResource.opened.get())
        assertEquals(1, CloseCountingResource.closed.get(), "closed exactly once, with the caller")
    }


    @Test
    fun ownedStepValueSurvivesLiveEdit() {
        val outcome = migrateAtBarrier("test/script/resource/script-closeable-step-test.yaml", stepsToBarrier = 2) {
            assertEquals(1, CloseCountingResource.opened.get(), "parked after Open")
            assertEquals(0, CloseCountingResource.closed.get(), "open at the barrier")
        }

        assertEquals("step-1", assertIs<Outcome.Success>(outcome).value.mainBoundaryValue(),
            "the resumed Script reads the carried value")
        assertEquals(1, CloseCountingResource.opened.get(), "Open was replayed, not re-run")
        assertEquals(1, CloseCountingResource.closed.get(), "closed exactly once, at the resumed run's settle")
    }


    @Test
    fun carriedLoopHoldIsReleasedOnReRecordAfterLiveEdit() {
        val outcome = migrateAtBarrier("test/script/resource/script-closeable-loop-test.yaml", stepsToBarrier = 5) {
            assertEquals(1, CloseCountingResource.opened.get(), "parked inside the loop after the first Open")
            assertEquals(0, CloseCountingResource.closed.get(), "open at the barrier")
        }

        assertEquals("0,1,2", assertIs<Outcome.Success>(outcome).value.mainBoundaryValue(),
            "the carried first-iteration hold is released when the resumed loop re-records the slot")
        assertEquals(3, CloseCountingResource.opened.get())
        assertEquals(3, CloseCountingResource.closed.get())
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun runScript(documentPathString: String): Outcome {
        CloseCountingResource.reset()
        context = KzenAutoContext.forTest()

        val scriptLocation = scriptLocation(documentPathString)
        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinition = AutoTestUtils.graphDefinitionAttempt(graphNotation).transitiveSuccessful

        val logic = LogicCompiler.compile(scriptLocation, graphNotation, graphDefinition, compilerServices())

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


    /**
     * Park the run after [stepsToBarrier] boundaries (counted as in
     * [ScriptExtensibilityTest][tech.kzen.auto.server.exec.script.ScriptExtensibilityTest]: the first boundary is
     * BEFORE the first step), run [atBarrier], drive [RunEngine.migrate] with a recompiled Logic, then resume to
     * a terminal outcome.
     */
    private fun migrateAtBarrier(
        documentPathString: String,
        stepsToBarrier: Int,
        atBarrier: () -> Unit
    ): Outcome {
        CloseCountingResource.reset()
        context = KzenAutoContext.forTest()

        val scriptLocation = scriptLocation(documentPathString)
        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinition = AutoTestUtils.graphDefinitionAttempt(graphNotation).transitiveSuccessful

        fun compile() = LogicCompiler.compile(scriptLocation, graphNotation, graphDefinition, compilerServices())

        val engine = RunEngine(compile(), context.objectStableMapper.objectStableId(scriptLocation))
        return try {
            repeat(stepsToBarrier) {
                engine.step()
                engine.awaitQuiescent()
            }
            atBarrier()

            engine.migrate(compile(), paused = false)
            runBlocking { engine.await() }
        }
        finally {
            engine.close()
        }
    }


    private fun scriptLocation(documentPathString: String): ObjectLocation {
        return ObjectLocation(DocumentPath.parse(documentPathString), ObjectPath.parse("main"))
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
