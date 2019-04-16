package tech.kzen.auto.server.objects.query

import tech.kzen.auto.common.paradigm.common.api.StatefulObject
import tech.kzen.auto.common.paradigm.dataflow.Dataflow
import tech.kzen.auto.common.paradigm.dataflow.input.RequiredInput
import tech.kzen.auto.common.paradigm.imperative.model.ExecutionValue


class CountSink(
        private val input: RequiredInput<*>
):
        Dataflow,
        StatefulObject
{
    //-----------------------------------------------------------------------------------------------------------------
    private var count = 0


    //-----------------------------------------------------------------------------------------------------------------
    override fun inspect(): ExecutionValue {
        return ExecutionValue.of(count)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun process() {
        input.get()
        count++
    }
}