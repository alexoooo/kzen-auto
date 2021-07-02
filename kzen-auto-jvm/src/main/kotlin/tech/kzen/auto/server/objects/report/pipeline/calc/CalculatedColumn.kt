package tech.kzen.auto.server.objects.report.pipeline.calc

import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.objects.report.pipeline.input.model.header.RecordHeader


interface CalculatedColumn<T> {
    fun evaluate(
        model: T,
        flatFileRecord: FlatFileRecord,
        recordHeader: RecordHeader
    ): ColumnValue
}