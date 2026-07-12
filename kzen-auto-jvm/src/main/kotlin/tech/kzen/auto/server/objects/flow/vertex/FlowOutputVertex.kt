package tech.kzen.auto.server.objects.flow.vertex

import tech.kzen.auto.common.paradigm.flow.api.StatelessFlowVertex
import tech.kzen.auto.common.paradigm.flow.api.input.RequiredInput
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.reflect.Reflect


/**
 * A Flow result, modelled as a sink vertex with a single input (and no output — it is a terminal node).
 * [FlowRun][tech.kzen.auto.server.exec.flow.FlowRun] special-cases it (like [FlowInputVertex]): it
 * captures the upstream input as this vertex's message and harvests that into the run's result
 * [tech.kzen.lib.common.exec.tuple.TupleValue] under [tupleComponentName], so [process] is never called.
 * [FlowDocument][tech.kzen.auto.server.objects.flow.FlowDocument] reads [tupleComponentName] to build
 * the logic signature's outputs.
 */
@Reflect
class FlowOutputVertex(
    @Suppress("unused") private val input: RequiredInput<Any?>,
    result: String
):
    StatelessFlowVertex
{
    val tupleComponentName = TupleComponentName(result)


    override fun process() {
        // No-op: FlowRun captures the upstream input as this sink's value.
    }
}
