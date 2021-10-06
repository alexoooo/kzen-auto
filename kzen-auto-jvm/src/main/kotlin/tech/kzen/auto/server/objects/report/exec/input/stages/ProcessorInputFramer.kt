package tech.kzen.auto.server.objects.report.exec.input.stages

import tech.kzen.auto.plugin.api.DataFramer
import tech.kzen.auto.plugin.model.data.DataBlockBuffer
import tech.kzen.auto.server.objects.report.exec.ReportProcessorStage


class ProcessorInputFramer(
    private val dataFramer: DataFramer
):
    ReportProcessorStage<DataBlockBuffer>("input-frame")
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun onEvent(event: DataBlockBuffer, sequence: Long, endOfBatch: Boolean) {
        frame(event)
    }


    fun frame(data: DataBlockBuffer) {
        data.frames.clear()
        dataFramer.frame(data)
    }
}