package tech.kzen.auto.server.objects.report.exec.output.export.format

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.util.data.DataLocationGroup
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.objects.report.exec.ReportProcessorStage
import tech.kzen.auto.server.objects.report.exec.event.ProcessorOutputEvent
import tech.kzen.auto.server.objects.report.exec.output.export.model.ExportFormat


class ExportFormatter(
    format: ExportFormat,
    private val filteredColumns: HeaderListing
):
    ReportProcessorStage<ProcessorOutputEvent<*>>("export-format")
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
                FlatFileRecord.of(filteredColumns.values),
                exportData)

            previousGroup = event.group
        }

        recordFormat.format(event.normalizedRow, exportData)
    }
}