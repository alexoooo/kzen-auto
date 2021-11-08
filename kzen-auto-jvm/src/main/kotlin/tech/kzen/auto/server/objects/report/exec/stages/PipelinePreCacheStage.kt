package tech.kzen.auto.server.objects.report.exec.stages

import com.lmax.disruptor.EventHandler
import tech.kzen.auto.server.objects.report.exec.ReportPipelineStage
import tech.kzen.auto.server.objects.report.exec.event.ReportOutputEvent


class PipelinePreCacheStage(
    private val partitionNumber: Long,
    private val partitionCount: Long
):
    ReportPipelineStage<ReportOutputEvent<*>>("cache")
{
    companion object {
        fun partitions(partitionCount: Int): Array<EventHandler<ReportOutputEvent<*>>> {
            return Array(partitionCount) { partitionNumber ->
                PipelinePreCacheStage(partitionNumber.toLong(), partitionCount.toLong())
            }
        }
    }


    private val i128 = LongArray(2)


    override fun onEvent(event: ReportOutputEvent<*>, sequence: Long, endOfBatch: Boolean) {
        if (event.isSkipOrSentinel()) {
            return
        }

        val partition = sequence % partitionCount

        if (partitionNumber != partition) {
            return
        }

        event.row.populateCaches(i128)
    }
}