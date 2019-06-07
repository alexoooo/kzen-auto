package tech.kzen.auto.server.objects.query

import tech.kzen.auto.common.paradigm.dataflow.api.StatelessDataflow
import tech.kzen.auto.common.paradigm.dataflow.api.input.RequiredInput
import tech.kzen.auto.common.paradigm.dataflow.api.output.OptionalOutput


@Suppress("unused")
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