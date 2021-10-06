package tech.kzen.auto.server.objects.report.exec.input.stages

import tech.kzen.auto.plugin.api.managed.PipelineOutput
import tech.kzen.auto.plugin.helper.DataFrameFeeder
import tech.kzen.auto.plugin.model.DataInputEvent
import tech.kzen.auto.plugin.model.data.DataBlockBuffer
import tech.kzen.auto.server.objects.report.exec.ReportProcessorStage
import tech.kzen.auto.server.objects.report.exec.trace.ReportInputTrace


class ProcessorFrameFeeder(
    output: PipelineOutput<DataInputEvent>,
    private val reportInputTrace: ReportInputTrace? = null
):
    ReportProcessorStage<DataBlockBuffer>("input-feed")
{
    //-----------------------------------------------------------------------------------------------------------------
    private val dataFrameFeeder = DataFrameFeeder(output)


    //-----------------------------------------------------------------------------------------------------------------
    override fun onEvent(event: DataBlockBuffer, sequence: Long, endOfBatch: Boolean) {
        val count = dataFrameFeeder.feed(event)

        if (count != 0 && reportInputTrace != null) {
            reportInputTrace.nextRecords(count)
        }
    }
}