package tech.kzen.auto.server.objects.report.pipeline.stages

import tech.kzen.auto.server.objects.report.ReportWorkPool
import tech.kzen.auto.server.objects.report.pipeline.ProcessorPipelineStage
import tech.kzen.auto.server.objects.report.pipeline.event.ProcessorOutputEvent
import tech.kzen.auto.server.objects.report.pipeline.output.TableReportOutput


class ProcessorOutputTableStage(
    val tableReportOutput: TableReportOutput,
    private val reportWorkPool: ReportWorkPool
):
    ProcessorPipelineStage<ProcessorOutputEvent<*>>("output")
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun onEvent(event: ProcessorOutputEvent<*>, sequence: Long, endOfBatch: Boolean) {
        if (endOfBatch) {
            // NB: must be done regardless of filterAllow to avoid lock due to starvation
            tableReportOutput.handlePreviewRequest(reportWorkPool)
        }

        if (event.skip) {
            return
        }

        tableReportOutput.add(event.row, event.header.value)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun close(error: Boolean) {
        tableReportOutput.close(error)
    }
}