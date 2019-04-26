package tech.kzen.auto.server.objects.query

import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.auto.common.paradigm.dataflow.api.Dataflow
import tech.kzen.auto.common.paradigm.dataflow.api.input.RequiredInput


class CountSink(
        private val input: RequiredInput<*>
):
        Dataflow<CountSink.State>
{
    //-----------------------------------------------------------------------------------------------------------------
    class State(
            var count: Int = 0
    )


    override fun inspectState(state: State): ExecutionValue {
        return ExecutionValue.of(state.count)
    }


    override fun initialState(): State {
        return State()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun process(state: State): State {
        input.get()
        return state
    }
}