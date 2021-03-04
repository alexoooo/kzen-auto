package tech.kzen.auto.server.objects.report.pipeline.cache

import com.lmax.disruptor.EventHandler
import tech.kzen.auto.server.objects.report.pipeline.event.ReportRecordEvent


class ReportPreCache(
    private val partitionNumber: Long,
    private val partitionCount: Long
):
    EventHandler<ReportRecordEvent>
{
    companion object {
        fun partitions(partitionCount: Int): Array<EventHandler<ReportRecordEvent>> {
//            check(partitionCount > 0)
            return Array(partitionCount) { partitionNumber ->
                ReportPreCache(partitionNumber.toLong(), partitionCount.toLong())
            }
        }
    }


    override fun onEvent(event: ReportRecordEvent, sequence: Long, endOfBatch: Boolean) {
        if (! event.filterAllow) {
            return
        }

        val partition = sequence % partitionCount

        if (partitionNumber != partition) {
            return
        }

        event.record.row.populateCaches()
    }
}