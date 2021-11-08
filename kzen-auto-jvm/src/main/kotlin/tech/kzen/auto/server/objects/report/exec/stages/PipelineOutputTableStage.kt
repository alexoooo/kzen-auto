package tech.kzen.auto.server.objects.report.exec.stages

import tech.kzen.auto.common.objects.document.report.output.OutputTableInfo
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotValueTableSpec
import tech.kzen.auto.server.objects.report.exec.ReportPipelineStage
import tech.kzen.auto.server.objects.report.exec.event.ReportOutputEvent
import tech.kzen.auto.server.objects.report.exec.output.TableReportOutput


class PipelineOutputTableStage(
    private val tableReportOutput: TableReportOutput
):
    ReportPipelineStage<ReportOutputEvent<*>>("output")
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun onEvent(event: ReportOutputEvent<*>, sequence: Long, endOfBatch: Boolean) {
        if (endOfBatch) {
            // NB: must be done regardless of filterAllow to avoid lock due to starvation
            tableReportOutput.handlePreviewRequest()
        }

        if (event.isSkipOrSentinel()) {
            event.completeAndClearSentinel()
            return
        }

        tableReportOutput.add(event.row, event.header.value)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun preview(
        pivotValueTableSpec: PivotValueTableSpec,
        start: Long,
        count: Int
    ): OutputTableInfo? {
        return tableReportOutput.previewFromOtherThread(pivotValueTableSpec, start, count)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun close(error: Boolean) {
        tableReportOutput.close(error)
    }
}