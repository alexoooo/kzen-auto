package tech.kzen.auto.server.objects.flow.vertex

import tech.kzen.auto.common.paradigm.flow.api.FlowRunOutput
import tech.kzen.auto.common.paradigm.flow.api.StatelessFlowVertex
import tech.kzen.auto.common.paradigm.flow.api.input.RequiredInput
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.reflect.Reflect


/**
 * A Flow result, modelled as a sink vertex with a single input (and no output — it is a terminal node).
 *
 * The harvest is the [FlowRunOutput] capability contract: the runner captures the upstream input as this
 * vertex's message and puts that in the run's result
 * [tech.kzen.lib.common.exec.tuple.TupleValue] under [tupleComponentName], so [process] is never called. This
 * archetype is also what puts the result in the Flow's declared signature, which
 * [FlowConventions][tech.kzen.auto.common.objects.document.flow.FlowConventions] derives from notation.
 */
@Reflect
class FlowOutputVertex(
    @Suppress("unused") private val input: RequiredInput<Any?>,
    result: String
):
    StatelessFlowVertex,
    FlowRunOutput
{
    override val tupleComponentName = TupleComponentName(result)


    override fun process() {
        // No-op: FlowRun captures the upstream input as this sink's value.
    }
}
