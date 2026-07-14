package tech.kzen.auto.server.exec.flow

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.paradigm.flow.model.exec.VisualVertexModel
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.engine.Address
import tech.kzen.lib.common.exec.engine.LogicFailure
import tech.kzen.lib.common.exec.engine.NodeStatus
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.engine.PauseReason
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
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


/**
 * End-to-end: load a real Flow notation, translate it with [FlowLogicCompiler], and walk its dataflow DAG on
 * the [RunEngine] — the Flow analogue of [tech.kzen.auto.server.exec.script.ScriptNotationTest]. Covers an input
 * argument flowing through to the harvested output, a stateless processor, a stream source looping across
 * iterations to its last value, and a vertex error settling the run failed.
 *
 * Pause-on-error (logic-spec §4) is also covered: the same failing vertex parks the run Suspended(Error) when
 * the toggle is on (via [tech.kzen.lib.common.exec.engine.Execution.recoverable] in [FlowRun]) rather than
 * failing — the engine-level mechanics are proven in [tech.kzen.lib.server.exec.engine.RunEngineTest].
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
    fun vertexErrorPausesWhenPauseOnError() {
        // The same divide-by-zero vertex, but with pause-on-error on: instead of failing the run it parks the
        // run Suspended(Error) at the failed vertex (a regular in-line vertex runs on the root node, so the root
        // parks) for inspect / fix + resume.
        val engine = engineFor("test/flow-error-test.yaml", argument("n", 6))
        try {
            engine.pauseOnError(true)
            engine.resume()
            engine.awaitQuiescent()

            val status = engine.snapshot().root.status
            assertEquals(PauseReason.Error, assertIs<NodeStatus.Suspended>(status).reason)
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun appendTextRunsWithOnlyOptionalSuffixWired() {
        // AppendText declares two OptionalInputs; only `suffix` is wired (the FlowInput sits above
        // its column). Its "possibly one" contract holds: the vertex runs with `prefix` empty and
        // emits the suffix text alone.
        val outcome = runFlow("test/flow-optional-input-test.yaml", argument("x", "hello"))
        assertEquals("hello", assertIs<Outcome.Success>(outcome).value.find(TupleComponentName("out")))
    }


    @Test
    fun selectLastMergesWhicheverBranchProducedEachIteration() {
        // Reduced FizzBuzz Flow Loop shape: SelectLast's two OptionalInputs are BOTH wired, but
        // per iteration only one branch may produce (the filter drops odd values). An empty
        // wired optional must not gate readiness — SelectLast runs every iteration with
        // whichever branch produced (1 -> 1, 2 -> "Even", 3 -> 3; the loop keeps the last).
        val outcome = runFlow("test/flow-select-last-test.yaml")
        assertEquals(3, assertIs<Outcome.Success>(outcome).value.find(TupleComponentName("last")))
    }


    @Test
    fun unwiredRequiredInputRefusesToCompile() {
        val failure = assertFailsWith<LogicFailure> {
            engineFor("test/flow-invalid-unwired-required-test.yaml")
        }
        assertTrue(failure.message!!.contains("Required input 'input' of 'FinvOutput'"))
    }


    @Test
    fun duplicateParameterNameRefusesToCompile() {
        val failure = assertFailsWith<LogicFailure> {
            engineFor("test/flow-invalid-duplicate-parameter-test.yaml")
        }
        assertTrue(failure.message!!.contains("Duplicate input parameter name: 'x'"))
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


    @Test
    fun arbitraryDomainObjectMessageDoesNotKillRun() {
        // Tracing is non-fatal: a non-basic message (a data class ExecutionValue.ofArbitrary can't render)
        // is rendered via toString instead of failing the run, even though inspection runs outside
        // pause-on-error's reach.
        val widget = ArbitraryMessage(7)
        val engine = engineFor("test/flow-execution-test.yaml", argument("x", widget))
        try {
            val outcome = runBlocking {
                engine.resume()
                engine.await()
            }
            assertEquals(widget, assertIs<Outcome.Success>(outcome).value.find(TupleComponentName("out")))

            // History retains every frame (the run-end flush clears the latest live message), so the input
            // vertex's toString rendering is present.
            val rendered = tracedMessages(engine, "test/flow-execution-test.yaml", "FxAlpha")
            assertTrue(rendered.contains(widget.toString()), "traced messages: $rendered")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun streamSinkFinalStateTracedAtRunEnd() {
        // The run-end force-flush must re-emit every vertex's final frame even when throttling dropped
        // intermediate ones: the AccumulateSink's traced state holds the whole accumulation after the run.
        val engine = engineFor("test/flow-accumulate-test.yaml")
        try {
            runBlocking {
                engine.resume()
                engine.await()
            }
            val sink = assertNotNull(tracedVertex(engine, "test/flow-accumulate-test.yaml", "FaccSink"))
            assertEquals(listOf("1", "2", "3"), assertNotNull(sink.state).get())
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun streamRunDoesNotRebuildGraphPerVertex() {
        // The run builds its graph once, so a 1..2000 stream through a 3-vertex chain finishes well under a
        // generous bound — wall-clock stays linear in the item count rather than paying a graph build per
        // vertex execution.
        val startNanos = System.nanoTime()
        val outcome = runFlow("test/flow-benchmark-test.yaml")
        val elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000
        assertIs<Outcome.Success>(outcome)
        assertTrue(elapsedMillis < 10_000, "stream of 2000 took ${elapsedMillis}ms")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private data class ArbitraryMessage(val id: Int) {
        override fun toString(): String {
            return "widget#$id"
        }
    }


    private fun argument(name: String, value: Any?): TupleValue {
        return TupleValue(listOf(
            TupleComponentValue(TupleComponentName(name), value)))
    }


    private fun runFlow(documentPathString: String, inputs: TupleValue = TupleValue.empty): Outcome {
        val engine = engineFor(documentPathString, inputs)
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


    private fun engineFor(documentPathString: String, inputs: TupleValue = TupleValue.empty): RunEngine {
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
                context.scriptValidationCache,
                context.flowMessageInspector,
                context.notationMetadataReader,
                context.jobWorkPool,
                LogicRunExecutionId.random()))

        return RunEngine(flowLogic, context.objectStableMapper.objectStableId(flowLocation), inputs)
    }


    // The latest emitted VisualVertexModel for a vertex (FlowRun is the run root, so every vertex trace
    // lands in root.live keyed by the vertex's stable id).
    private fun tracedVertex(engine: RunEngine, documentPathString: String, vertexName: String): VisualVertexModel? {
        val emitted = engine.snapshot().root.live[vertexAddress(documentPathString, vertexName)]
            ?: return null
        @Suppress("UNCHECKED_CAST")
        return VisualVertexModel.fromCollection(emitted.get() as Map<String, Any?>)
    }


    // Every message value a vertex ever traced (via the append-only history), decoded back to its raw form.
    private fun tracedMessages(engine: RunEngine, documentPathString: String, vertexName: String): List<Any?> {
        val address = vertexAddress(documentPathString, vertexName)
        return engine.history(0)
            .filter { it.address == address }
            .mapNotNull {
                @Suppress("UNCHECKED_CAST")
                VisualVertexModel.fromCollection(it.value.get() as Map<String, Any?>).message?.get()
            }
    }


    private fun vertexAddress(documentPathString: String, vertexName: String): Address {
        val vertexLocation = ObjectLocation(DocumentPath.parse(documentPathString), ObjectPath.parse(vertexName))
        return Address.of(context.objectStableMapper.objectStableId(vertexLocation).value)
    }
}
