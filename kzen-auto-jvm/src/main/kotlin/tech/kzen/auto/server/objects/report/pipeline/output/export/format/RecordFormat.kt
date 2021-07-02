package tech.kzen.auto.server.objects.report.pipeline.output.export.format

import tech.kzen.auto.plugin.model.data.DataRecordBuffer
import tech.kzen.auto.plugin.model.record.FlatFileRecord


interface RecordFormat {
    fun format(record: FlatFileRecord, output: DataRecordBuffer)
}