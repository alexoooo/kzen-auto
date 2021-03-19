package tech.kzen.auto.server.objects.report.pipeline.stages

import com.lmax.disruptor.EventHandler
import tech.kzen.auto.server.objects.report.pipeline.event.ProcessorOutputEvent
import tech.kzen.auto.server.objects.report.pipeline.summary.ReportSummary


class ProcessorSummaryStage(
    val reportSummary: ReportSummary
):
    EventHandler<ProcessorOutputEvent<*>>, AutoCloseable
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