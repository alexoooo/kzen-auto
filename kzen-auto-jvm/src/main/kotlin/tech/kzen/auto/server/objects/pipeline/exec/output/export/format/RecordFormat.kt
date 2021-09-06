package tech.kzen.auto.server.objects.pipeline.exec.output.export.format

import tech.kzen.auto.plugin.model.data.DataRecordBuffer
import tech.kzen.auto.plugin.model.record.FlatFileRecord


interface RecordFormat {
    fun format(record: FlatFileRecord, output: DataRecordBuffer)
}