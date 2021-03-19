package tech.kzen.auto.server.objects.report.pipeline.calc

import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordRowBuffer
import tech.kzen.auto.server.objects.report.pipeline.input.model.header.RecordHeader


interface CalculatedColumn {
    fun evaluate(
            recordLineBuffer: RecordRowBuffer,
            recordHeader: RecordHeader
    ): String
}