package tech.kzen.auto.server.objects.report.pipeline.output.export.format

import tech.kzen.auto.common.util.data.DataLocationGroup
import tech.kzen.auto.server.objects.report.pipeline.ProcessorPipelineStage
import tech.kzen.auto.server.objects.report.pipeline.event.ProcessorOutputEvent
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.objects.report.pipeline.output.export.model.ExportFormat


class ExportFormatter(
    val format: ExportFormat
):
    ProcessorPipelineStage<ProcessorOutputEvent<*>>("export-format")
{
    //-----------------------------------------------------------------------------------------------------------------
    private var recordFormat = when (format) {
        ExportFormat.Tsv -> TsvExportFormatter()
        ExportFormat.Csv -> CsvExportFormatter()
    }

    private var previousGroup: DataLocationGroup? = null


    //-----------------------------------------------------------------------------------------------------------------
    override fun onEvent(event: ProcessorOutputEvent<*>, sequence: Long, endOfBatch: Boolean) {
        if (event.skip) {
            return
        }

        val exportData = event.exportData
        exportData.clear()

        if (previousGroup != event.group) {
            recordFormat.format(
                FlatFileRecord.of(event.header.value.headerNames.values),
                exportData)

            previousGroup = event.group
        }

        recordFormat.format(event.row, exportData)
    }
}