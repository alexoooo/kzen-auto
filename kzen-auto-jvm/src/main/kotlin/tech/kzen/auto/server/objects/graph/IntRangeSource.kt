package tech.kzen.auto.server.objects.graph

import tech.kzen.auto.common.paradigm.dataflow.api.StreamDataflow
import tech.kzen.auto.common.paradigm.dataflow.api.output.StreamOutput
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class IntRangeSource(
        private val output: StreamOutput<Int>,

        private val from: Int,
        private val to: Int
):
        StreamDataflow<IntRangeSource.State>/*,
        ValidatedObject*/
{
    //-----------------------------------------------------------------------------------------------------------------
    class State(
            var next: Int
    )


    override fun initialState(): State {
        return State(from)
    }


    override fun inspectState(state: State): ExecutionValue {
        return ExecutionValue.of(
                mapOf("next" to state.next))
    }


//    //-----------------------------------------------------------------------------------------------------------------
//    override fun validate(): String? {
//        if (from > to) {
//            return "'From' must be less than or equal to 'To'"
//        }
//
//        return null
//    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun process(state: State): State {
        if (state.next <= to) {
            next(state)
        }
        return state
    }


    override fun next(state: State): State {
        output.set(state.next, state.next < to)
        state.next++
        return state
    }
}