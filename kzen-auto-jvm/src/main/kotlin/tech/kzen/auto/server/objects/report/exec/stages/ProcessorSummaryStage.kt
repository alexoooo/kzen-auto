package tech.kzen.auto.server.objects.report.exec.stages

import tech.kzen.auto.server.objects.report.exec.ReportProcessorStage
import tech.kzen.auto.server.objects.report.exec.event.ProcessorOutputEvent
import tech.kzen.auto.server.objects.report.exec.summary.ReportSummary


class ProcessorSummaryStage(
    val reportSummary: ReportSummary
):
    ReportProcessorStage<ProcessorOutputEvent<*>>("summary"),
    AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun onEvent(event: ProcessorOutputEvent<*>, sequence: Long, endOfBatch: Boolean) {
        if (endOfBatch) {
            reportSummary.handleViewRequest()
        }

        if (event.skip) {
            return
        }

        reportSummary.add(event.row, event.header.value)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
        reportSummary.close()
    }
}