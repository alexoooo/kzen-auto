package tech.kzen.auto.server.objects.report.pipeline.event.v2

import tech.kzen.auto.plugin.model.ModelOutputEvent
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordHeaderBuffer
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordRowBuffer


class ProcessorOutputEvent<T>:
    ModelOutputEvent<T>()
{
    val row =  RecordRowBuffer()

    val header = RecordHeaderBuffer()

    var filterAllow: Boolean = false
}