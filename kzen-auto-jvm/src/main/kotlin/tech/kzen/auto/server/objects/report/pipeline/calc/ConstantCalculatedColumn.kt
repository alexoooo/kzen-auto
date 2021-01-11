package tech.kzen.auto.server.objects.report.pipeline.calc

import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordItemBuffer
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordHeader


class ConstantCalculatedColumn(
    private val value: String
): CalculatedColumn {
    companion object {
        val empty = ConstantCalculatedColumn("")
        val error = ConstantCalculatedColumn("<error>")
    }


    override fun evaluate(
        recordLineBuffer: RecordItemBuffer,
        recordHeader: RecordHeader
    ): String {
        return value
    }
}