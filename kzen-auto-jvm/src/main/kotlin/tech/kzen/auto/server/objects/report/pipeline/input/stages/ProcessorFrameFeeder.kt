package tech.kzen.auto.server.objects.report.pipeline.input.stages

import com.lmax.disruptor.EventHandler
import tech.kzen.auto.plugin.api.managed.PipelineOutput
import tech.kzen.auto.plugin.helper.DataFrameFeeder
import tech.kzen.auto.plugin.model.DataBlockBuffer
import tech.kzen.auto.plugin.model.DataInputEvent
import tech.kzen.auto.server.objects.report.pipeline.progress.ReportProgressTracker


class ProcessorFrameFeeder(
    output: PipelineOutput<DataInputEvent>,
    private val streamProgressTracker: ReportProgressTracker.Buffer? = null
):
    EventHandler<DataBlockBuffer>
{
    //-----------------------------------------------------------------------------------------------------------------
    private val dataFrameFeeder = DataFrameFeeder(output)


    //-----------------------------------------------------------------------------------------------------------------
    override fun onEvent(event: DataBlockBuffer, sequence: Long, endOfBatch: Boolean) {
        val count = dataFrameFeeder.feed(event)

        if (count != 0 && streamProgressTracker != null) {
            streamProgressTracker.nextRecords(count)
        }
    }
}