package tech.kzen.auto.server.objects.graph

import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.auto.common.paradigm.dataflow.api.Dataflow
import tech.kzen.auto.common.paradigm.dataflow.api.input.RequiredInput
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class CountSink(
        private val input: RequiredInput<*>
):
        Dataflow<CountSink.State>
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