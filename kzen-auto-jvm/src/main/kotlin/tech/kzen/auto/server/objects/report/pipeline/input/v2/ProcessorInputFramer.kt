package tech.kzen.auto.server.objects.report.pipeline.input.v2

import tech.kzen.auto.plugin.api.DataFramer
import tech.kzen.auto.plugin.model.DataBlockBuffer


class ProcessorInputFramer(
    private val dataFramer: DataFramer
) {
    //-----------------------------------------------------------------------------------------------------------------
    @Suppress("UNUSED_PARAMETER")
    fun frameDisruptor(
        data: DataBlockBuffer,
        sequence: Long,
        endOfBatch: Boolean
    ) {
        frame(data)
    }


    fun frame(data: DataBlockBuffer) {
        data.frames.clear()
        dataFramer.frame(data)
    }
}