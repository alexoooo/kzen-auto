package tech.kzen.auto.server.objects.report.pipeline.event.v2

import tech.kzen.auto.plugin.model.ModelOutputEvent
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordMapBuffer


class ProcessorOutputEvent<T>
    : ModelOutputEvent<T>()
{
    val record: RecordMapBuffer = RecordMapBuffer()
    var filterAllow: Boolean = false
}