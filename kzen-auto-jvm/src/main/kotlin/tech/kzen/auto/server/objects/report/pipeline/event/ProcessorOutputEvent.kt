package tech.kzen.auto.server.objects.report.pipeline.event

import tech.kzen.auto.plugin.model.ModelOutputEvent
import tech.kzen.auto.server.objects.report.pipeline.input.model.header.RecordHeaderBuffer
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordRowBuffer


class ProcessorOutputEvent<T>:
    ModelOutputEvent<T>()
{
    override val row = RecordRowBuffer()

    val header = RecordHeaderBuffer()
}