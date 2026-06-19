package tech.kzen.auto.server.objects.flow.vertex

import tech.kzen.auto.common.paradigm.flow.api.StatelessFlowVertex
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.reflect.Reflect


/**
 * A Flow input parameter, modelled as a source vertex (no predecessors). Its emitted message is the
 * value of the run argument named [parameter] — analogous to the Script paradigm's
 * [tech.kzen.auto.server.objects.script.step.value.ArgumentStep], but as a graph vertex.
 *
 * The argument value is injected by [tech.kzen.auto.server.objects.flow.FlowExecution] from the run's
 * [tech.kzen.lib.common.exec.tuple.TupleValue] arguments (a [process] call cannot reach them), so
 * [process] is intentionally a no-op. [FlowDocument][tech.kzen.auto.server.objects.flow.FlowDocument]
 * reads [tupleComponentName] to build the logic signature's inputs.
 */
@Reflect
class FlowInputVertex(
    parameter: String
):
    StatelessFlowVertex
{
    val tupleComponentName = TupleComponentName(parameter)


    override fun process() {
        // No-op: the message is seeded from the run arguments by FlowExecution.
    }
}
