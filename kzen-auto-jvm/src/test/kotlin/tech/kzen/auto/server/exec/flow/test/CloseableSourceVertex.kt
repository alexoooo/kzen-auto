package tech.kzen.auto.server.exec.flow.test

import tech.kzen.auto.common.paradigm.flow.api.StatelessFlowVertex
import tech.kzen.auto.common.paradigm.flow.api.output.RequiredOutput
import tech.kzen.auto.server.objects.job.worker.test.CloseCountingResource
import tech.kzen.lib.common.reflect.Reflect


/**
 * Test-only source whose message is a fresh `AutoCloseable` ([CloseCountingResource]) it does nothing else with,
 * so what becomes of it is entirely [tech.kzen.auto.server.exec.flow.FlowRun]'s recording of the vertex's output.
 */
@Reflect
class CloseableSourceVertex(
    private val output: RequiredOutput<Any?>
):
    StatelessFlowVertex
{
    override fun process() {
        output.set(CloseCountingResource("flow-source"))
    }
}
