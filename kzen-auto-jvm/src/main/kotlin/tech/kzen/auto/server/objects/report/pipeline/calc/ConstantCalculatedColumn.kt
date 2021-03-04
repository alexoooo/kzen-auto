package tech.kzen.auto.server.objects.report.pipeline.calc

import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordHeader
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordRowBuffer


class ConstantCalculatedColumn(
    private val value: String
): CalculatedColumn {
    companion object {
        val empty = ConstantCalculatedColumn("")
        val error = ConstantCalculatedColumn(ColumnValue.errorText)
    }


    override fun evaluate(
            recordLineBuffer: RecordRowBuffer,
            recordHeader: RecordHeader
    ): String {
        return value
    }
}