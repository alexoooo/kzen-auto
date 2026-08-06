package tech.kzen.auto.server.exec.script

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompilerServices
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


/**
 * The implicit result end to end, on the real [RunEngine]: a Script declaring a `main` result and containing no
 * Result step yields its LAST ROOT STEP's value, mirroring how an IfStep returns its taken branch's terminal and
 * a ForEachStep collects its body's. A Script declaring no result is void — its last step's value is discarded —
 * and a Result step that ran anywhere, nested branches included, supplies the result instead.
 *
 * The last root step is a value something reads, which is what [ScriptValueReferences] must report for it: a
 * trailing loop that is the implicit result has to COLLECT, or the Script returns a well-typed empty List.
 */
class ScriptImplicitResultTest {
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
    fun lastStepValueIsTheResultWhenNoResultStep() {
        val outcome = runScript("test/script/result/implicit-result-test.yaml")
        assertEquals(42, assertIs<Outcome.Success>(outcome).value.mainComponentValue())
    }


    @Test
    fun trailingLoopIsCollectedWhenItIsTheImplicitResult() {
        val outcome = runScript("test/script/result/implicit-result-loop-test.yaml")
        assertEquals(listOf(2, 4, 6), assertIs<Outcome.Success>(outcome).value.mainComponentValue())
    }


    @Test
    fun voidScriptDiscardsTheLastStepValue() {
        val outcome = runScript("test/script/result/implicit-result-void-test.yaml")
        assertEquals(TupleValue.empty, assertIs<Outcome.Success>(outcome).value)
    }


    @Test
    fun nestedResultStepStillWinsOverTheImplicitLastStep() {
        // The Result runs inside the taken branch, ending the Script — so the trailing Tail (0) never runs.
        val outcome = runScript("test/script/result/implicit-result-nested-result-test.yaml")
        assertEquals(5, assertIs<Outcome.Success>(outcome).value.mainComponentValue())
    }


    @Test
    fun hostedSubScriptReturnsItsImplicitResult() {
        // The child's implicit result (7) is the caller's RunStep value, and the caller's own last root step
        // computes Call + 1 = 8 — implicitly, in turn.
        val outcome = runScript("test/script/result/implicit-result-parent-test.yaml")
        assertEquals(8, assertIs<Outcome.Success>(outcome).value.mainComponentValue())
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun runScript(documentPathString: String): Outcome {
        ScriptStepTestModule.register()

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
