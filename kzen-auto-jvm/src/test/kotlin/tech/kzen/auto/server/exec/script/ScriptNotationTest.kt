package tech.kzen.auto.server.exec.script

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.exec.tuple.TupleComponentValue
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
 * End-to-end: load a real Script notation, translate it with [ScriptLogicCompiler] (reusing the existing
 * graph definition / validation / Kotlin-expression compilation), and run it on the new [RunEngine] —
 * proving the notation-wiring path produces correct results with real compiled expressions, control flow,
 * literals, bindings, and parameters.
 */
class ScriptNotationTest {
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
    fun foreachDoublesEachItemAndSums() {
        val outcome = runScript("test/script-engine-foreach-test.yaml")
        assertEquals(12, assertIs<Outcome.Success>(outcome).value.mainComponentValue())
    }


    @Test
    fun ifRunsTheSelectedBranch() {
        val outcome = runScript("test/script-engine-if-test.yaml")
        assertEquals(10, assertIs<Outcome.Success>(outcome).value.mainComponentValue())
    }


    @Test
    fun parameterDefaultUsedWhenNoInput() {
        val outcome = runScript("test/script-engine-parameter-test.yaml")
        assertEquals(4, assertIs<Outcome.Success>(outcome).value.mainComponentValue())
    }


    @Test
    fun parameterFromRunInput() {
        val inputs = TupleValue(listOf(
            TupleComponentValue(TupleComponentName("Start"), 5)))
        val outcome = runScript("test/script-engine-parameter-test.yaml", inputs)
        assertEquals(10, assertIs<Outcome.Success>(outcome).value.mainComponentValue())
    }


    @Test
    fun literalAndWaitProduceTheLiteral() {
        val outcome = runScript("test/script-engine-literal-wait-test.yaml")
        assertEquals("hello", assertIs<Outcome.Success>(outcome).value.mainComponentValue())
    }


    @Test
    fun doWhileRunsBodyAndCapturesResult() {
        val outcome = runScript("test/script-engine-dowhile-test.yaml")
        assertEquals(7, assertIs<Outcome.Success>(outcome).value.mainComponentValue())
    }


    @Test
    fun runStepInvokesChildScriptWithArgument() {
        val outcome = runScript("test/script-engine-run-test.yaml")
        assertEquals(7, assertIs<Outcome.Success>(outcome).value.mainComponentValue())
    }


    @Test
    fun largeForeachOverAFormulaStaysFast() {
        // Regression guard for the loaded-class cache: a 1000-iteration ForEach whose body is a Formula (plus the
        // sum + result Formulas) evaluates thousands of times. Rebuilding a URLClassLoader per evaluation would
        // take orders of magnitude longer than this bound; the cached load keeps it well under a second.
        val start = System.nanoTime()
        val outcome = runScript("test/script-engine-foreach-benchmark-test.yaml")
        val elapsedMillis = (System.nanoTime() - start) / 1_000_000

        assertEquals(1_001_000, assertIs<Outcome.Success>(outcome).value.mainComponentValue())
        assertTrue(elapsedMillis < 20_000, "1000-iteration ForEach took ${elapsedMillis}ms")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun runScript(documentPathString: String, inputs: TupleValue = TupleValue.empty): Outcome {
        context = KzenAutoContext.forTest()

        val documentPath = DocumentPath.parse(documentPathString)
        val scriptLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))

        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinition = AutoTestUtils.graphDefinitionAttempt(graphNotation).transitiveSuccessful

        val scriptLogic = ScriptLogicCompiler.compile(
            scriptLocation,
            graphNotation,
            graphDefinition,
            LogicCompilerServices(
                context.graphEnvironment,
                context.objectStableMapper,
                context.cachedKotlinCompiler,
                context.scriptValidationCache,
                context.notationMetadataReader,
                context.jobWorkPool,
                LogicRunExecutionId.random()))

        val engine = RunEngine(scriptLogic, context.objectStableMapper.objectStableId(scriptLocation), inputs)
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
}
