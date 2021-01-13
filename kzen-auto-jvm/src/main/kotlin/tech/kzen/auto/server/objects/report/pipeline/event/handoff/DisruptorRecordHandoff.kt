package tech.kzen.auto.server.objects.report.pipeline.event.handoff

import com.lmax.disruptor.RingBuffer
import tech.kzen.auto.server.objects.report.pipeline.event.ReportRecordEvent
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordMapBuffer


class DisruptorRecordHandoff(
    private val recordRingBuffer: RingBuffer<ReportRecordEvent>
): RecordHandoff {
    private var sequence: Long = -1


    override fun next(): RecordMapBuffer {
        sequence = recordRingBuffer.next()
        val event = recordRingBuffer.get(sequence)
        return event.record
    }


    override fun commit() {
        recordRingBuffer.publish(sequence)
    }
}