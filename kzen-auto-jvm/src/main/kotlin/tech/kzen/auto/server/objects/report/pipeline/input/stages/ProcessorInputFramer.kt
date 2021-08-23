package tech.kzen.auto.server.objects.report.pipeline.input.stages

import tech.kzen.auto.plugin.api.DataFramer
import tech.kzen.auto.plugin.model.data.DataBlockBuffer
import tech.kzen.auto.server.objects.pipeline.exec.PipelineProcessorStage


class ProcessorInputFramer(
    private val dataFramer: DataFramer
):
    PipelineProcessorStage<DataBlockBuffer>("input-frame")
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