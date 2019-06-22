package tech.kzen.auto.server.objects.query

import tech.kzen.auto.common.paradigm.dataflow.api.StatelessDataflow
import tech.kzen.auto.common.paradigm.dataflow.api.input.RequiredInput
import tech.kzen.auto.common.paradigm.dataflow.api.output.BatchOutput


@Suppress("unused")
class RepeatProcessor<T>(
        private val input: RequiredInput<T>,
        private val output: BatchOutput<T>,

        private val times: Int
): StatelessDataflow {
    override fun process() {
        val value = input.get()

        for (i in 1 .. times) {
            output.add(value)
        }
    }
}