package tech.kzen.auto.server.objects.flow.vertex

import tech.kzen.auto.common.paradigm.flow.api.StatelessFlowVertex
import tech.kzen.auto.common.paradigm.flow.api.input.OptionalInput
import tech.kzen.auto.common.paradigm.flow.api.output.RequiredOutput
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class SelectLast<T>(
        private val first: OptionalInput<T>,
        private val second: OptionalInput<T>,
        private val output: RequiredOutput<T>
): StatelessFlowVertex {
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