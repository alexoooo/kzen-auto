package tech.kzen.auto.server.exec.script

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.engine.Outcome
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
            context.graphEnvironment,
            context.objectStableMapper,
            context.cachedKotlinCompiler)

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
