package tech.kzen.auto.server.objects.report.pipeline.calc

import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordHeader
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordItemBuffer


interface CalculatedColumn {
    fun evaluate(
        recordLineBuffer: RecordItemBuffer,
        recordHeader: RecordHeader
    ): String
}