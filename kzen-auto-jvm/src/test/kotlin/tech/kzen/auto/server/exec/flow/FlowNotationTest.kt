package tech.kzen.auto.server.exec.flow

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompilerServices
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
 * End-to-end: load a real Flow notation, translate it with [FlowLogicCompiler], and walk its dataflow DAG on
 * the [RunEngine] — the Flow analogue of [tech.kzen.auto.server.exec.script.ScriptNotationTest]. Re-proves on
 * the engine the cases [tech.kzen.auto.server.objects.flow.FlowExecutionTest] drove against the retired
 * re-entrant executor: an input argument flowing through to the harvested output, a stateless processor, a
 * stream source looping across iterations to its last value, and a vertex error settling the run failed.
 *
 * Pause-on-error is intentionally not covered — it is not yet wired into the engine (a tracked parity gap
 * shared with the Script port).
 */
class FlowNotationTest {
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
    fun inputArgumentFlowsToOutputResult() {
        val outcome = runFlow("test/flow-execution-test.yaml", argument("x", 42))
        assertEquals(42, assertIs<Outcome.Success>(outcome).value.find(TupleComponentName("out")))
    }


    @Test
    fun replaceProcessorProducesConstant() {
        val outcome = runFlow("test/flow-step-test.yaml", argument("x", "ignored-by-replace"))
        assertEquals("Z", assertIs<Outcome.Success>(outcome).value.find(TupleComponentName("out")))
    }


    @Test
    fun streamSourceDrivesIterationsToLastValue() {
        val outcome = runFlow("test/flow-stream-test.yaml")
        assertEquals(3, assertIs<Outcome.Success>(outcome).value.find(TupleComponentName("last")))
    }


    @Test
    fun vertexErrorFailsTheRun() {
        val outcome = runFlow("test/flow-error-test.yaml", argument("n", 6))
        assertIs<Outcome.Failed>(outcome)
    }


    @Test
    fun runLogicVertexHostsChildScript() {
        // FlowInput(x) -> RunLogic(child Script `number + 1`) -> FlowOutput(out). The vertex's single upstream
        // message is bound to the callee's first parameter (`number`), and the callee's result becomes the
        // vertex message — exercising FlowRun.runChildVertex's Execution.host + cross-flavour LogicCompiler
        // dispatch (a Flow hosting a Script).
        val outcome = runFlow("test/flow-run-test.yaml", argument("x", 6))
        assertEquals(7, assertIs<Outcome.Success>(outcome).value.find(TupleComponentName("out")))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun argument(name: String, value: Any?): TupleValue {
        return TupleValue(listOf(
            TupleComponentValue(TupleComponentName(name), value)))
    }


    private fun runFlow(documentPathString: String, inputs: TupleValue = TupleValue.empty): Outcome {
        context = KzenAutoContext.forTest()

        val documentPath = DocumentPath.parse(documentPathString)
        val flowLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))

        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinition = AutoTestUtils.graphDefinitionAttempt(graphNotation).transitiveSuccessful

        val flowLogic = FlowLogicCompiler.compile(
            flowLocation,
            graphNotation,
            graphDefinition,
            LogicCompilerServices(
                context.graphEnvironment,
                context.objectStableMapper,
                context.cachedKotlinCompiler,
                context.flowMessageInspector,
                context.notationMetadataReader))

        val engine = RunEngine(flowLogic, context.objectStableMapper.objectStableId(flowLocation), inputs)
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
