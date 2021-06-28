package tech.kzen.auto.server.objects.report.pipeline.input.stages

import tech.kzen.auto.plugin.api.DataFramer
import tech.kzen.auto.plugin.model.DataBlockBuffer
import tech.kzen.auto.server.objects.report.pipeline.ProcessorPipelineStage


class ProcessorInputFramer(
    private val dataFramer: DataFramer
):
    ProcessorPipelineStage<DataBlockBuffer>("input-frame")
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