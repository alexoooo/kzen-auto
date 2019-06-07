package tech.kzen.auto.server.objects.query

import tech.kzen.auto.common.paradigm.dataflow.api.StatelessDataflow
import tech.kzen.auto.common.paradigm.dataflow.api.input.OptionalInput
import tech.kzen.auto.common.paradigm.dataflow.api.output.RequiredOutput


@Suppress("unused")
class AppendText(
        private val first: OptionalInput<Any>,
        private val second: OptionalInput<Any>,
        private val output: RequiredOutput<String>
): StatelessDataflow {
    override fun process() {
        val firstText = first.get()?.toString() ?: ""
        val secondText = second.get()?.toString() ?: ""
        output.set(firstText + secondText)
    }
}