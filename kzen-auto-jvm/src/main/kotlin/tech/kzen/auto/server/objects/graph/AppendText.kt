package tech.kzen.auto.server.objects.graph

import tech.kzen.auto.common.paradigm.dataflow.api.StatelessDataflow
import tech.kzen.auto.common.paradigm.dataflow.api.input.OptionalInput
import tech.kzen.auto.common.paradigm.dataflow.api.output.RequiredOutput


@Suppress("unused")
class AppendText(
        private val prefix: OptionalInput<Any>,
        private val suffix: OptionalInput<Any>,
        private val output: RequiredOutput<String>
): StatelessDataflow {
    override fun process() {
        val firstText = prefix.get()?.toString() ?: ""
        val secondText = suffix.get()?.toString() ?: ""
        output.set(firstText + secondText)
    }
}