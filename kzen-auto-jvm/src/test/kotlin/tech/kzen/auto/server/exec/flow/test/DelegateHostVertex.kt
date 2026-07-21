package tech.kzen.auto.server.exec.flow.test

import tech.kzen.auto.common.paradigm.flow.api.FlowLogicHost
import tech.kzen.auto.common.paradigm.flow.api.StatelessFlowVertex
import tech.kzen.auto.common.paradigm.flow.api.input.RequiredInput
import tech.kzen.auto.common.paradigm.flow.api.output.RequiredOutput
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


/**
 * Test-only [FlowLogicHost]: the minimal shape a third party would write. It deliberately does not override
 * `arguments`, so a run through it also proves the interface default is enough to host a callee.
 */
@Reflect
class DelegateHostVertex(
    override val instructions: ObjectLocation,
    @Suppress("unused") private val input: RequiredInput<Any?>,
    @Suppress("unused") private val output: RequiredOutput<Any?>
):
    StatelessFlowVertex,
    FlowLogicHost
{
    override fun process() {
        throw IllegalStateException("DelegateHostVertex is executed by FlowRun, not process()")
    }
}
