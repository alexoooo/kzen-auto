package tech.kzen.auto.server.objects.report.calc

import tech.kzen.auto.server.objects.report.input.model.RecordHeader
import tech.kzen.auto.server.objects.report.input.model.RecordLineBuffer


interface CalculatedColumn {
    fun evaluate(
        recordLineBuffer: RecordLineBuffer,
        recordHeader: RecordHeader
    ): String
}