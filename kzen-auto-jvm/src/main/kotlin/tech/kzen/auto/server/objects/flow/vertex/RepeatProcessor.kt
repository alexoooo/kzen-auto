package tech.kzen.auto.server.objects.flow.vertex

import tech.kzen.auto.common.paradigm.flow.api.StatelessFlowVertex
import tech.kzen.auto.common.paradigm.flow.api.input.RequiredInput
import tech.kzen.auto.common.paradigm.flow.api.output.BatchOutput
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class RepeatProcessor<T>(
        private val input: RequiredInput<T>,
        private val output: BatchOutput<T>,

        private val times: Int
): StatelessFlowVertex {
    override fun process() {
        val value = input.get()

        for (i in 1 .. times) {
            output.add(value)
        }
    }
}