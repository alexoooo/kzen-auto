package tech.kzen.auto.server.objects.report.exec.calc

import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.objects.report.exec.input.model.header.RecordHeader


interface CalculatedColumn<T> {
    // TODO: primitive and Any return type handling for performance
    fun evaluate(
        model: T,
        flatFileRecord: FlatFileRecord,
        recordHeader: RecordHeader
    ): ColumnValue
}