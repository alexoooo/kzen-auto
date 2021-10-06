package tech.kzen.auto.server.objects.report.exec.event.output

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


    override fun batch(size: Int, processor: (T) -> Unit) {
        val last = ringBuffer.next(size)
        for (i in size - 1 downTo 0) {
            val event = ringBuffer.get(last - i)
            processor(event)
        }
        ringBuffer.publish(last - size, last)
    }
}