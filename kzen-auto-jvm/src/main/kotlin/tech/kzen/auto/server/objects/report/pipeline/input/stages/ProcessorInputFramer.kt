package tech.kzen.auto.server.objects.report.pipeline.input.stages

import com.lmax.disruptor.EventHandler
import tech.kzen.auto.plugin.api.DataFramer
import tech.kzen.auto.plugin.model.DataBlockBuffer


class ProcessorInputFramer(
    private val dataFramer: DataFramer
):
    EventHandler<DataBlockBuffer>
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