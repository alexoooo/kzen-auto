package tech.kzen.auto.server.objects.pipeline.exec.stages

import tech.kzen.auto.server.objects.pipeline.exec.PipelineProcessorStage
import tech.kzen.auto.server.objects.report.pipeline.event.ProcessorOutputEvent
import tech.kzen.auto.server.objects.report.pipeline.summary.ReportSummary


class ProcessorSummaryStage(
    val reportSummary: ReportSummary
):
    PipelineProcessorStage<ProcessorOutputEvent<*>>("summary"),
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