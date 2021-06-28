package tech.kzen.auto.server.objects.report.pipeline.output.export.format

import tech.kzen.auto.plugin.model.RecordDataBuffer
import tech.kzen.auto.server.objects.report.pipeline.input.model.FlatFileRecord


interface RecordFormat {
    fun format(record: FlatFileRecord, output: RecordDataBuffer)
}