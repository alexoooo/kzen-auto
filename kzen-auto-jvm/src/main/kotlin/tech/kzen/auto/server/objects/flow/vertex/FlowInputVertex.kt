package tech.kzen.auto.server.objects.flow.vertex

import tech.kzen.auto.common.paradigm.flow.api.StatelessFlowVertex
import tech.kzen.auto.common.paradigm.flow.api.output.RequiredOutput
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.reflect.Reflect


/**
 * A Flow input parameter, modelled as a source vertex (no predecessors). Its emitted message is the
 * value of the run argument named [parameter] — analogous to a Script parameter
 * ([ParameterBinding][tech.kzen.auto.server.objects.script.binding.ParameterBinding]), but as a graph
 * vertex. Downstream vertices wire to it via the [output] channel.
 *
 * The argument value is injected by [FlowRun][tech.kzen.auto.server.exec.flow.FlowRun] from the run's
 * [tech.kzen.lib.common.exec.tuple.TupleValue] arguments (a [process] call cannot reach them), so
 * [process] is intentionally a no-op and [output] is never written — the channel is declared only so
 * the vertex renders an egress funnel. [FlowDocument][tech.kzen.auto.server.objects.flow.FlowDocument]
 * reads [tupleComponentName] to build the logic signature's inputs.
 */
@Reflect
class FlowInputVertex(
    parameter: String,
    @Suppress("unused") private val output: RequiredOutput<Any?>
):
    StatelessFlowVertex
{
    val tupleComponentName = TupleComponentName(parameter)


    override fun process() {
        // No-op: the message is seeded from the run arguments by FlowRun.
    }
}
