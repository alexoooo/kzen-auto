package tech.kzen.auto.server.objects.pipeline.exec.stages

import tech.kzen.auto.common.objects.document.report.output.OutputTableInfo
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotValueTableSpec
import tech.kzen.auto.server.objects.pipeline.exec.PipelineProcessorStage
import tech.kzen.auto.server.objects.report.pipeline.event.ProcessorOutputEvent
import tech.kzen.auto.server.objects.report.pipeline.output.TableReportOutput


class ProcessorOutputTableStage(
    val tableReportOutput: TableReportOutput,
//    private val reportWorkPool: ReportWorkPool
):
    PipelineProcessorStage<ProcessorOutputEvent<*>>("output")
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun onEvent(event: ProcessorOutputEvent<*>, sequence: Long, endOfBatch: Boolean) {
        if (endOfBatch) {
            // NB: must be done regardless of filterAllow to avoid lock due to starvation
            tableReportOutput.handlePreviewRequest()

//            tableReportOutput.handlePreviewRequest(reportWorkPool)
        }

        if (event.skip) {
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