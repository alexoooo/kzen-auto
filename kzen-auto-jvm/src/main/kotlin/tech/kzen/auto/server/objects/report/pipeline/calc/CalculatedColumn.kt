package tech.kzen.auto.server.objects.report.pipeline.calc

import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordItemBuffer
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordHeader


interface CalculatedColumn {
    fun evaluate(
        recordLineBuffer: RecordItemBuffer,
        recordHeader: RecordHeader
    ): String
}