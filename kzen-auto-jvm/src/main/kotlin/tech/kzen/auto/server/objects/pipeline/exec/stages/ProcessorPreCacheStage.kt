package tech.kzen.auto.server.objects.pipeline.exec.stages

import com.lmax.disruptor.EventHandler
import tech.kzen.auto.server.objects.pipeline.exec.PipelineProcessorStage
import tech.kzen.auto.server.objects.pipeline.exec.event.ProcessorOutputEvent


class ProcessorPreCacheStage(
    private val partitionNumber: Long,
    private val partitionCount: Long
):
    PipelineProcessorStage<ProcessorOutputEvent<*>>("cache")
{
    companion object {
        fun partitions(partitionCount: Int): Array<EventHandler<ProcessorOutputEvent<*>>> {
            return Array(partitionCount) { partitionNumber ->
                ProcessorPreCacheStage(partitionNumber.toLong(), partitionCount.toLong())
            }
        }
    }


    private val i128 = LongArray(2)


    override fun onEvent(event: ProcessorOutputEvent<*>, sequence: Long, endOfBatch: Boolean) {
        if (event.skip) {
            return
        }

        val partition = sequence % partitionCount

        if (partitionNumber != partition) {
            return
        }

        event.row.populateCaches(i128)
    }
}