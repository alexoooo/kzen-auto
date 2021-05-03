package tech.kzen.auto.server.objects.report.pipeline.event

import tech.kzen.auto.plugin.model.ModelOutputEvent
import tech.kzen.auto.server.objects.report.pipeline.input.model.FlatFileRecord
import tech.kzen.auto.server.objects.report.pipeline.input.model.header.RecordHeaderBuffer


class ProcessorOutputEvent<T>:
    ModelOutputEvent<T>()
{
    override val row = FlatFileRecord()

    val header = RecordHeaderBuffer()
}