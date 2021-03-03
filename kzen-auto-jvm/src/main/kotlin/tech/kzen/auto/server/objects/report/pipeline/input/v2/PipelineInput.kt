package tech.kzen.auto.server.objects.report.pipeline.input.v2

import tech.kzen.auto.plugin.model.DataInputEvent


class PipelineInput(
    private val input: ProcessorInputReader
) {
    fun poll(dataInputEvent: DataInputEvent): Boolean {
        return false
    }
}