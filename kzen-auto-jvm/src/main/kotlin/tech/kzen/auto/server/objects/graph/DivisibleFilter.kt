package tech.kzen.auto.server.objects.graph

import tech.kzen.auto.common.paradigm.dataflow.api.StatelessDataflow
import tech.kzen.auto.common.paradigm.dataflow.api.input.RequiredInput
import tech.kzen.auto.common.paradigm.dataflow.api.output.OptionalOutput
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class DivisibleFilter(
        private val input: RequiredInput<Int>,
        private val output: OptionalOutput<Int>,

        private val divisor: Int
): StatelessDataflow {
    override fun process() {
        val value = input.get()

        val remainder = value % divisor

        if (remainder == 0) {
            output.set(value)
        }
    }
}