package tech.kzen.auto.server.objects.flow.vertex

import tech.kzen.auto.common.paradigm.flow.api.FlowVertex
import tech.kzen.auto.common.paradigm.flow.api.input.RequiredInput
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class CountSink(
    private val input: RequiredInput<*>
):
    FlowVertex<CountSink.State>
{
    //-----------------------------------------------------------------------------------------------------------------
    class State(
        var count: Long = 0
    )


    override fun initialState(): State {
        return State()
    }


    override fun inspectState(state: State): ExecutionValue {
        return ExecutionValue.of(state.count)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun process(state: State): State {
        input.get()
        state.count++
        return state
    }
}