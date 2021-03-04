package tech.kzen.auto.server.objects.report.pipeline.input.v2

import tech.kzen.auto.plugin.model.DataInputEvent


class ProcessorPipeline(
    private val input: ProcessorInputReader
) {
    fun poll(dataInputEvent: DataInputEvent): Boolean {
        return false
    }
}