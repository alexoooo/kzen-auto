package tech.kzen.auto.server.objects.flow.vertex

import tech.kzen.auto.common.paradigm.flow.api.FlowLogicHost
import tech.kzen.auto.common.paradigm.flow.api.StatelessFlowVertex
import tech.kzen.auto.common.paradigm.flow.api.input.RequiredInput
import tech.kzen.auto.common.paradigm.flow.api.output.RequiredOutput
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


/**
 * [RunLogicVertex] with two wired inputs: [first] binds the callee's first declared parameter and [second] its
 * second, with [arguments] binding text literals to any further parameter by name.
 *
 * A separate archetype rather than a second input on `RunLogic`, because a vertex occupies one grid column per
 * declared input: widening `RunLogic` would move every existing document's downstream geometry. Both inputs
 * are required, so the vertex runs only once both upstream messages are present.
 */
@Reflect
class RunLogicVertex2(
    override val instructions: ObjectLocation,
    override val arguments: Map<String, String>,
    @Suppress("unused") private val first: RequiredInput<Any?>,
    @Suppress("unused") private val second: RequiredInput<Any?>,
    @Suppress("unused") private val output: RequiredOutput<Any?>
):
    StatelessFlowVertex,
    FlowLogicHost
{
    override fun process() {
        throw IllegalStateException("RunLogicVertex2 is executed by FlowRun, not process()")
    }
}
