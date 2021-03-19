package tech.kzen.auto.server.objects.report.pipeline.stages

import com.lmax.disruptor.EventHandler
import tech.kzen.auto.server.objects.report.pipeline.event.ProcessorOutputEvent


class ProcessorPreCacheStage(
    private val partitionNumber: Long,
    private val partitionCount: Long
):
    EventHandler<ProcessorOutputEvent<*>>
{
    companion object {
        fun partitions(partitionCount: Int): Array<EventHandler<ProcessorOutputEvent<*>>> {
            return Array(partitionCount) { partitionNumber ->
                ProcessorPreCacheStage(partitionNumber.toLong(), partitionCount.toLong())
            }
        }
    }


    override fun onEvent(event: ProcessorOutputEvent<*>, sequence: Long, endOfBatch: Boolean) {
        if (event.skip) {
            return
        }

        val partition = sequence % partitionCount

        if (partitionNumber != partition) {
            return
        }

        event.row.populateCaches()
    }
}