package tech.kzen.auto.server.objects.report.pipeline.stages

import com.lmax.disruptor.EventHandler
import tech.kzen.auto.server.objects.report.ReportWorkPool
import tech.kzen.auto.server.objects.report.pipeline.event.ProcessorOutputEvent
import tech.kzen.auto.server.objects.report.pipeline.output.ReportOutput


class ProcessorOutputStage(
    val reportOutput: ReportOutput,
    private val reportWorkPool: ReportWorkPool
):
    EventHandler<ProcessorOutputEvent<*>>
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun onEvent(event: ProcessorOutputEvent<*>, sequence: Long, endOfBatch: Boolean) {
        if (endOfBatch) {
            // NB: must be done regardless of filterAllow to avoid lock due to starvation
            reportOutput.handlePreviewRequest(reportWorkPool)
        }

        if (event.skip) {
            return
        }

        reportOutput.add(event.row, event.header.value)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun close(error: Boolean) {
        reportOutput.close(error)
    }
}