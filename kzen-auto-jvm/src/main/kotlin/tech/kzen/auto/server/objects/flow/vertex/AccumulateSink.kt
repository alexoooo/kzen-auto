package tech.kzen.auto.server.objects.flow.vertex

import tech.kzen.auto.common.paradigm.flow.api.FlowVertex
import tech.kzen.auto.common.paradigm.flow.api.input.RequiredInput
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class AccumulateSink(
    private val input: RequiredInput<Any>
):
    FlowVertex<AccumulateSink.State>
{
    //-----------------------------------------------------------------------------------------------------------------
    class State(
        var values: MutableList<Any>
    )


    override fun inspectState(state: State): ExecutionValue {
        return ExecutionValue.of(
            state.values.map {
                it.toString()
            })
    }


    override fun initialState(): State {
        return State(mutableListOf())
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun process(state: State): State {
        val value: Any = input.get()
        state.values.add(value)
        return state
    }
}