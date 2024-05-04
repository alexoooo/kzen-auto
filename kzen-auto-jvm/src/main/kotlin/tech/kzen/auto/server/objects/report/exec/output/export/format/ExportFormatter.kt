package tech.kzen.auto.server.objects.report.exec.output.export.format

import kotlinx.datetime.Clock
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.output.OutputExportSpec
import tech.kzen.auto.common.util.data.DataLocationGroup
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.objects.report.exec.ReportPipelineStage
import tech.kzen.auto.server.objects.report.exec.event.ReportOutputEvent
import tech.kzen.auto.server.objects.report.exec.output.export.model.ExportFormat
import tech.kzen.lib.common.model.document.DocumentName
import java.nio.file.Path
import java.nio.file.Paths


class ExportFormatter(
    format: ExportFormat,
    private val filteredColumns: HeaderListing,
    private val reportName: DocumentName,
    private val outputExportSpec: OutputExportSpec
):
    ReportPipelineStage<ReportOutputEvent<*>>("export-format")
{
    //-----------------------------------------------------------------------------------------------------------------
    private var recordFormat = when (format) {
        ExportFormat.Tsv -> TsvExportFormatter()
        ExportFormat.Csv -> CsvExportFormatter()
    }

    private var previousGroup: DataLocationGroup? = null
    private var previousExportPath: Path? = null
    private var previousInnerFilename: String? = null
    private var previousStartTime = Clock.System.now()


    //-----------------------------------------------------------------------------------------------------------------
    override fun onEvent(event: ReportOutputEvent<*>, sequence: Long, endOfBatch: Boolean) {
        if (event.isSkipOrSentinel()) {
            return
        }

        val exportData = event.exportData
        exportData.clear()

        if (previousGroup != event.group) {
            val pathChanged = onNewGroup(event.group)

            if (pathChanged) {
                // print flat file header
                recordFormat.format(
                    FlatFileRecord.of(filteredColumns.values.map { it.render() }),
                    exportData)
            }
        }

        event.exportPath = previousExportPath!!
        event.innerFilename = previousInnerFilename!!
        recordFormat.format(event.normalizedRow, exportData)

//        if (exportData.charsLength == 0) {
//            println("foo")
//        }
    }


    private fun onNewGroup(group: DataLocationGroup): Boolean {
        val previousTimeGroupResolvedPattern = outputExportSpec.resolvePath(reportName, group, previousStartTime)
        val previousTimeExportPath = Paths.get(previousTimeGroupResolvedPattern).toAbsolutePath().normalize()

        if (previousExportPath == previousTimeExportPath) {
            return false
        }

        val startTime = Clock.System.now()

        val groupResolvedPattern = outputExportSpec.resolvePath(reportName, group, startTime)
        previousExportPath = Paths.get(groupResolvedPattern).toAbsolutePath().normalize()

        previousInnerFilename = outputExportSpec.resolveInnerFilename(reportName, group, startTime)

        previousGroup = group
        previousStartTime = startTime

        return true
    }
}