package tech.kzen.auto.server.objects.graph

import tech.kzen.auto.common.paradigm.dataflow.api.StatelessDataflow
import tech.kzen.auto.common.paradigm.dataflow.api.input.RequiredInput
import tech.kzen.auto.common.paradigm.dataflow.api.output.OptionalOutput
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class ReplaceProcessor(
        private val input: RequiredInput<*>,
        private val output: OptionalOutput<String>,

        private val replacement: String
): StatelessDataflow {
    override fun process() {
        input.get()

        output.set(replacement)
    }
}