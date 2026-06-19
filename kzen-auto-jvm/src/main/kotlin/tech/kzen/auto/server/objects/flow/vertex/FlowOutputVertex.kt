package tech.kzen.auto.server.objects.flow.vertex

import tech.kzen.auto.common.paradigm.flow.api.StatelessFlowVertex
import tech.kzen.auto.common.paradigm.flow.api.input.RequiredInput
import tech.kzen.auto.common.paradigm.flow.api.output.RequiredOutput
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.reflect.Reflect


/**
 * A Flow result, modelled as a sink vertex with a single input. It passes its input value straight
 * through to its output so the standard output-draining path captures it as the vertex message;
 * [tech.kzen.auto.server.objects.flow.FlowExecution] then harvests that message into the run's result
 * [tech.kzen.lib.common.exec.tuple.TupleValue] under [tupleComponentName].
 * [FlowDocument][tech.kzen.auto.server.objects.flow.FlowDocument] reads [tupleComponentName] to build
 * the logic signature's outputs.
 */
@Reflect
class FlowOutputVertex(
    private val input: RequiredInput<Any?>,
    private val output: RequiredOutput<Any?>,
    result: String
):
    StatelessFlowVertex
{
    val tupleComponentName = TupleComponentName(result)


    override fun process() {
        output.set(input.get())
    }
}
