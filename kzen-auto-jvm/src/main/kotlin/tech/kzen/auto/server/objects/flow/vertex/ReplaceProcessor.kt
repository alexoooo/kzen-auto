package tech.kzen.auto.server.objects.flow.vertex

import tech.kzen.auto.common.paradigm.flow.api.StatelessFlowVertex
import tech.kzen.auto.common.paradigm.flow.api.input.RequiredInput
import tech.kzen.auto.common.paradigm.flow.api.output.OptionalOutput
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class ReplaceProcessor(
        private val input: RequiredInput<*>,
        private val output: OptionalOutput<String>,

        private val replacement: String
): StatelessFlowVertex {
    override fun process() {
        input.get()

        output.set(replacement)
    }
}