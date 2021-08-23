package tech.kzen.auto.server.objects.report.pipeline.input.stages

import tech.kzen.auto.plugin.api.managed.PipelineOutput
import tech.kzen.auto.plugin.helper.DataFrameFeeder
import tech.kzen.auto.plugin.model.DataInputEvent
import tech.kzen.auto.plugin.model.data.DataBlockBuffer
import tech.kzen.auto.server.objects.pipeline.exec.PipelineTrace
import tech.kzen.auto.server.objects.pipeline.exec.PipelineProcessorStage


class ProcessorFrameFeeder(
    output: PipelineOutput<DataInputEvent>,
    private val pipelineTrace: PipelineTrace? = null
):
    PipelineProcessorStage<DataBlockBuffer>("input-feed")
{
    //-----------------------------------------------------------------------------------------------------------------
    private val dataFrameFeeder = DataFrameFeeder(output)


    //-----------------------------------------------------------------------------------------------------------------
    override fun onEvent(event: DataBlockBuffer, sequence: Long, endOfBatch: Boolean) {
        val count = dataFrameFeeder.feed(event)

        if (count != 0 && pipelineTrace != null) {
            pipelineTrace.nextRecords(count)
        }
    }
}