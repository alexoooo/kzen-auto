package tech.kzen.auto.server.objects.report.pipeline.event.v2

import com.lmax.disruptor.RingBuffer
import tech.kzen.auto.plugin.api.managed.PipelineOutput


class DisruptorPipelineOutput<T>(
    private val ringBuffer: RingBuffer<T>
): PipelineOutput<T> {
    private var sequence: Long = -1


    override fun next(): T {
        val nextSequence = ringBuffer.next()
        val event = ringBuffer.get(nextSequence)
        sequence = nextSequence
        return event
    }


    override fun commit() {
        ringBuffer.publish(sequence)
    }
}