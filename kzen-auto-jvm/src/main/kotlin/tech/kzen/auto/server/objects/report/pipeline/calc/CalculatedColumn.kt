package tech.kzen.auto.server.objects.report.pipeline.calc

import tech.kzen.auto.server.objects.report.pipeline.input.model.FlatDataRecord
import tech.kzen.auto.server.objects.report.pipeline.input.model.header.RecordHeader


interface CalculatedColumn {
    fun evaluate(
        recordLineBuffer: FlatDataRecord,
        recordHeader: RecordHeader
    ): String
}