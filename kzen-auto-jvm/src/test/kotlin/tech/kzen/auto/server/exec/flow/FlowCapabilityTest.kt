package tech.kzen.auto.server.exec.flow

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.paradigm.flow.model.exec.VisualVertexModel
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.engine.Address
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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


/**
 * The extensibility acceptance test for the Flow vertex SPI: [thirdPartyCapabilityVerticesRunWithNoSharedCodeEdit]
 * runs a Flow built from three vertex types defined ENTIRELY in the test source set — a
 * [FlowRunInput][tech.kzen.auto.common.paradigm.flow.api.FlowRunInput] source, a
 * [FlowLogicHost][tech.kzen.auto.common.paradigm.flow.api.FlowLogicHost] that invokes a child Script, and a
 * [FlowRunOutput][tech.kzen.auto.common.paradigm.flow.api.FlowRunOutput] sink — with no product archetype, no
 * entry in [FlowRun] or [FlowLogicCompiler], and no kzen `when`. If a third-party vertex can seed from run
 * arguments, host a child Logic and contribute to the result with no kzen edit, the vertex set is genuinely
 * extensible — the defect the capability interfaces set out to fix. Mirrors
 * [ScriptExtensibilityTest][tech.kzen.auto.server.exec.script.ScriptExtensibilityTest].
 *
 * The remaining tests pin the two channel contracts the runner and the channel enforce between them: a
 * `RequiredOutput` that emitted nothing, and a non-batch output set twice in one execution.
 */
class FlowCapabilityTest {
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
    fun thirdPartyCapabilityVerticesRunWithNoSharedCodeEdit() {
        val outcome = runFlow("test/flow/flow-capability-test.yaml", argument("aliased-x", 6))
        assertEquals(12, assertIs<Outcome.Success>(outcome).value.find(TupleComponentName("aliased-out")))
    }


    @Test
    fun vertexInspectMessageRendersTrace() {
        val engine = engineFor("test/flow/flow-capability-test.yaml", argument("aliased-x", 6))
        try {
            runBlocking {
                engine.resume()
                engine.await()
            }

            val rendered = tracedMessages(engine, "test/flow/flow-capability-test.yaml", "FcapInput")
            assertTrue(rendered.contains("inspected:6"), "traced messages: $rendered")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun requiredOutputNotSetFailsTheVertex() {
        val outcome = runFlow("test/flow/flow-required-output-test.yaml", argument("x", 6))
        assertIs<Outcome.Failed>(outcome)
    }


    @Test
    fun requiredOutputNotSetPausesWhenPauseOnError() {
        val engine = engineFor("test/flow/flow-required-output-test.yaml", argument("x", 6))
        try {
            engine.pauseOnError(true)
            engine.resume()
            engine.awaitQuiescent()

            val status = engine.snapshot().root.status
            assertEquals(PauseReason.Error, assertIs<NodeStatus.Suspended>(status).reason)

            val vertex = assertNotNull(
                tracedVertex(engine, "test/flow/flow-required-output-test.yaml", "FreqSilent"))
            assertTrue(
                assertNotNull(vertex.error).contains("Required output"),
                "vertex error: ${vertex.error}")
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun doubleSetWithoutDrainFailsTheVertex() {
        val engine = engineFor("test/flow/flow-double-emit-test.yaml", argument("x", 6))
        try {
            engine.pauseOnError(true)
            engine.resume()
            engine.awaitQuiescent()

            val vertex = assertNotNull(
                tracedVertex(engine, "test/flow/flow-double-emit-test.yaml", "FdupEmit"))
            assertTrue(
                assertNotNull(vertex.error).contains("already set"),
                "vertex error: ${vertex.error}")
        }
        finally {
            engine.close()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
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
                context.jobValidationCache,
                context.notationMetadataReader,
                context.jobWorkPool,
                LogicRunExecutionId.random()))

        return RunEngine(flowLogic, context.objectStableMapper.objectStableId(flowLocation), inputs)
    }


    private fun tracedVertex(engine: RunEngine, documentPathString: String, vertexName: String): VisualVertexModel? {
        val emitted = engine.snapshot().root.live[vertexAddress(documentPathString, vertexName)]
            ?: return null
        @Suppress("UNCHECKED_CAST")
        return VisualVertexModel.fromCollection(emitted.get() as Map<String, Any?>)
    }


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
