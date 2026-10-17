package tech.kzen.auto.server.exec.flow

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.exec.bindingsOf
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.objects.job.worker.test.CloseCountingResource
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.data.binding.BindingName
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
 * Flow ownership of what it records (resource-holds plan §3.4): a vertex's `AutoCloseable` output is held by the
 * Flow frame at the point [FlowRun] records it, so it is live for the downstream vertex (which throws if it
 * arrives closed, and emits its name as the run's output) and closed once when the Flow settles. The source
 * does nothing with the value but emit it, so what the test observes is the recording alone. Mirrors
 * [ScriptResourceHoldTest][tech.kzen.auto.server.exec.script.ScriptResourceHoldTest]; relies on the suite's
 * sequential execution for [CloseCountingResource]'s process-global counts.
 */
class FlowResourceHoldTest {
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
    fun ownedVertexOutputIsLiveDownstreamAndClosesAtSettle() {
        CloseCountingResource.reset()
        val outcome = runFlow("test/flow/flow-closeable-test.yaml")

        assertEquals("flow-source", JobDataValues.boundary(
            assertIs<Outcome.Success>(outcome).value.requireValue(BindingName("out"))),
            "the check vertex saw the source's value open")
        assertEquals(1, CloseCountingResource.opened.get())
        assertEquals(1, CloseCountingResource.closed.get(), "closed exactly once, at the Flow's settle")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun runFlow(documentPathString: String): Outcome {
        context = KzenAutoContext.forTest()

        val flowLocation = ObjectLocation(DocumentPath.parse(documentPathString), ObjectPath.parse("main"))
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

        val engine = RunEngine(
            flowLogic,
            context.objectStableMapper.objectStableId(flowLocation),
            bindingsOf(flowLogic.signature().inputs))
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
