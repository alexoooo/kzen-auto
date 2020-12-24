package tech.kzen.auto.server.objects.report.calc

import tech.kzen.auto.server.objects.report.input.model.RecordHeader
import tech.kzen.auto.server.objects.report.input.model.RecordLineBuffer


class ConstantCalculatedColumn(
    private val value: String
): CalculatedColumn {
    companion object {
        val empty = ConstantCalculatedColumn("")
        val error = ConstantCalculatedColumn("<error>")
    }


    override fun evaluate(
        recordLineBuffer: RecordLineBuffer,
        recordHeader: RecordHeader
    ): String {
        return value
    }
}