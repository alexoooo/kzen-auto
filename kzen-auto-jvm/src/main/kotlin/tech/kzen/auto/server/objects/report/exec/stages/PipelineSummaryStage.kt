package tech.kzen.auto.server.objects.report.exec.stages

import tech.kzen.auto.server.objects.report.exec.ReportPipelineStage
import tech.kzen.auto.server.objects.report.exec.event.ReportOutputEvent
import tech.kzen.auto.server.objects.report.exec.summary.ReportSummary


class PipelineSummaryStage(
    val reportSummary: ReportSummary
):
    ReportPipelineStage<ReportOutputEvent<*>>("summary"),
    AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun onEvent(event: ReportOutputEvent<*>, sequence: Long, endOfBatch: Boolean) {
        if (endOfBatch) {
            reportSummary.handleViewRequest()
        }

        if (event.isSkipOrSentinel()) {
            return
        }

        reportSummary.add(event.row, event.header.value)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
        reportSummary.close()
    }
}