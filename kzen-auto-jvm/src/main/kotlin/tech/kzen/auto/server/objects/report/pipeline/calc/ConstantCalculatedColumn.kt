package tech.kzen.auto.server.objects.report.pipeline.calc

import tech.kzen.auto.server.objects.report.pipeline.input.model.FlatFileRecord
import tech.kzen.auto.server.objects.report.pipeline.input.model.header.RecordHeader


class ConstantCalculatedColumn(
    private val value: String
): CalculatedColumn {
    companion object {
        val empty = ConstantCalculatedColumn("")
        val error = ConstantCalculatedColumn(ColumnValue.errorText)
    }


    override fun evaluate(
        recordLineBuffer: FlatFileRecord,
        recordHeader: RecordHeader
    ): String {
        return value
    }
}