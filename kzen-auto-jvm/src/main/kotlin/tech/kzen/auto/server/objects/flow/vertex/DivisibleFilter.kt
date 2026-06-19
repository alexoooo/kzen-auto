package tech.kzen.auto.server.objects.flow.vertex

import tech.kzen.auto.common.paradigm.flow.api.StatelessFlowVertex
import tech.kzen.auto.common.paradigm.flow.api.input.RequiredInput
import tech.kzen.auto.common.paradigm.flow.api.output.OptionalOutput
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class DivisibleFilter(
        private val input: RequiredInput<Int>,
        private val output: OptionalOutput<Int>,

        private val divisor: Int
): StatelessFlowVertex {
    override fun process() {
        val value = input.get()

        val remainder = value % divisor

        if (remainder == 0) {
            output.set(value)
        }
    }
}