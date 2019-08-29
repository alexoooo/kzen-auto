package tech.kzen.auto.server.objects.graph

import tech.kzen.auto.common.paradigm.dataflow.api.StatelessDataflow
import tech.kzen.auto.common.paradigm.dataflow.api.input.OptionalInput
import tech.kzen.auto.common.paradigm.dataflow.api.output.RequiredOutput


@Suppress("unused")
class SelectLast<T>(
        private val first: OptionalInput<T>,
        private val second: OptionalInput<T>,
        private val output: RequiredOutput<T>
): StatelessDataflow {
    override fun process() {
        val secondValue = second.get()
        if (secondValue != null) {
            output.set(secondValue)
            return
        }

        val firstValue = first.get()!!
        output.set(firstValue)
    }
}