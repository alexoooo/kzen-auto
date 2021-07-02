package tech.kzen.auto.server.objects.report.pipeline.calc

import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.objects.report.pipeline.input.model.header.RecordHeader


class ConstantCalculatedColumn<T>(
    private val value: ColumnValue
): CalculatedColumn<T> {
    companion object {
        fun <T> empty() =
            ConstantCalculatedColumn<T>(ColumnValue.ofScalar(""))

        fun <T> error() =
            ConstantCalculatedColumn<T>(ColumnValue.errorValue)
    }


    override fun evaluate(
        model: T,
        flatFileRecord: FlatFileRecord,
        recordHeader: RecordHeader
    ): ColumnValue {
        return value
    }
}