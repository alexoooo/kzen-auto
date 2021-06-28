package tech.kzen.auto.server.objects.report.pipeline.event

import tech.kzen.auto.common.util.data.DataLocationGroup
import tech.kzen.auto.plugin.model.ModelOutputEvent
import tech.kzen.auto.plugin.model.RecordDataBuffer
import tech.kzen.auto.server.objects.report.pipeline.input.model.FlatFileRecord
import tech.kzen.auto.server.objects.report.pipeline.input.model.header.RecordHeaderBuffer


class ProcessorOutputEvent<T>:
    ModelOutputEvent<T>()
{
    override val row = FlatFileRecord()

    val header = RecordHeaderBuffer()

//    var dataLocation = DataLocation.unknown
    var group = DataLocationGroup.empty
    val exportData = RecordDataBuffer()
}