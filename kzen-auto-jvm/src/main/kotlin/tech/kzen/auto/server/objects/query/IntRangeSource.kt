package tech.kzen.auto.server.objects.query

import tech.kzen.auto.common.paradigm.common.api.StatefulObject
import tech.kzen.auto.common.paradigm.common.api.ValidatedObject
import tech.kzen.auto.common.paradigm.dataflow.api.StreamDataflow
import tech.kzen.auto.common.paradigm.dataflow.api.output.StreamOutput
import tech.kzen.auto.common.paradigm.imperative.model.ExecutionValue


class IntRangeSource(
        private val output: StreamOutput<Int>,

        from: Int,
        private val to: Int
):
        StreamDataflow,
        StatefulObject,
        ValidatedObject
{
    //-----------------------------------------------------------------------------------------------------------------
    private var next = from


    //-----------------------------------------------------------------------------------------------------------------
    override fun validate(): String? {
        if (next > to) {
            return "'From' must be less than or equal to 'To'"
        }

        return null
    }


    override fun inspect(): ExecutionValue {
        return ExecutionValue.of(next)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun process() {
        if (next <= to) {
            next()
        }
    }


    override fun next() {
        output.set(next, next < to)
        next++
    }
}