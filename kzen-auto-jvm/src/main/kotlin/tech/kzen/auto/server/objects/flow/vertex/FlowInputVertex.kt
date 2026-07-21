package tech.kzen.auto.server.objects.flow.vertex

import tech.kzen.auto.common.paradigm.flow.api.FlowRunInput
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
 * The seeding is the [FlowRunInput] capability contract: the runner reads [tupleComponentName] from the run's
 * arguments and sets the message directly, so [process] is a no-op and [output] is never written — the channel
 * is declared only so the vertex renders an egress funnel. This archetype is also what puts the parameter in
 * the Flow's declared signature, which
 * [FlowConventions][tech.kzen.auto.common.objects.document.flow.FlowConventions] derives from notation.
 */
@Reflect
class FlowInputVertex(
    parameter: String,
    @Suppress("unused") private val output: RequiredOutput<Any?>
):
    StatelessFlowVertex,
    FlowRunInput
{
    override val tupleComponentName = TupleComponentName(parameter)


    override fun process() {
        // No-op: the message is seeded from the run arguments by FlowRun.
    }
}
