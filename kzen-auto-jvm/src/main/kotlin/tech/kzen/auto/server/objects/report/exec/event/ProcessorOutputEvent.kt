package tech.kzen.auto.server.objects.report.exec.event

import tech.kzen.auto.common.util.data.DataLocationGroup
import tech.kzen.auto.plugin.model.ModelOutputEvent
import tech.kzen.auto.plugin.model.data.DataRecordBuffer
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.objects.report.exec.input.model.header.RecordHeaderBuffer


class ProcessorOutputEvent<T>:
    ModelOutputEvent<T>()
{
    override val row = FlatFileRecord()
    val normalizedRow = FlatFileRecord()

    val header = RecordHeaderBuffer()

//    var dataLocation = DataLocation.unknown
    var group = DataLocationGroup.empty
    val exportData = DataRecordBuffer()
}