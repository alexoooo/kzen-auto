package tech.kzen.auto.server.objects.flow.vertex

import tech.kzen.auto.common.paradigm.flow.api.StatelessFlowVertex
import tech.kzen.auto.common.paradigm.flow.api.input.OptionalInput
import tech.kzen.auto.common.paradigm.flow.api.output.RequiredOutput
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class AppendText(
        private val prefix: OptionalInput<Any>,
        private val suffix: OptionalInput<Any>,
        private val output: RequiredOutput<String>
): StatelessFlowVertex {
    override fun process() {
        val firstText = prefix.get()?.toString() ?: ""
        val secondText = suffix.get()?.toString() ?: ""
        output.set(firstText + secondText)
    }
}