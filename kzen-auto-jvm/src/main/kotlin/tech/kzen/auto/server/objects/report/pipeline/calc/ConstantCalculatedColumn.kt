package tech.kzen.auto.server.objects.report.pipeline.calc

import tech.kzen.auto.server.objects.report.pipeline.input.model.header.RecordHeader
import tech.kzen.auto.server.objects.report.pipeline.input.model.FlatDataRecord


class ConstantCalculatedColumn(
    private val value: String
): CalculatedColumn {
    companion object {
        val empty = ConstantCalculatedColumn("")
        val error = ConstantCalculatedColumn(ColumnValue.errorText)
    }


    override fun evaluate(
        recordLineBuffer: FlatDataRecord,
        recordHeader: RecordHeader
    ): String {
        return value
    }
}