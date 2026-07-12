package tech.kzen.auto.common.paradigm.flow.model.channel

import tech.kzen.auto.common.paradigm.flow.api.output.BatchOutput
import tech.kzen.auto.common.paradigm.flow.api.output.OptionalOutput
import tech.kzen.auto.common.paradigm.flow.api.output.RequiredOutput
import tech.kzen.auto.common.paradigm.flow.api.output.StreamOutput


// TODO: enforce optional/required/stream/batch contracts
class MutableFlowOutput<T>:
    OptionalOutput<T>,
    RequiredOutput<T>,
    BatchOutput<T>,
    StreamOutput<T>
{
    private val buffer = mutableListOf<T>()
    private var streamHasNext: Boolean = false


    override fun set(payload: T) {
        buffer.add(payload)
        streamHasNext = false
    }


    override fun add(payload: T) {
        buffer.add(payload)
    }


    override fun set(payload: T, hasNext: Boolean) {
        buffer.add(payload)
        this.streamHasNext = hasNext
    }


    // Reset the injected channel to its pristine state. A vertex instance lives for the whole run, so the
    // runner calls this before each process(): a process() that set() an item then threw would otherwise
    // leave it buffered to re-emit on pause-on-error resume.
    fun clear() {
        buffer.clear()
        streamHasNext = false
    }


    fun bufferIsEmpty(): Boolean {
        return buffer.isEmpty()
    }

    fun bufferHasOne(): Boolean {
        return buffer.size == 1
    }

    fun bufferHasMultiple(): Boolean {
        return buffer.size > 1
    }

    fun streamHasNext(): Boolean {
        return streamHasNext
    }


    fun consumeAndClear(consumer: (T) -> Unit) {
        buffer.forEach(consumer)
        buffer.clear()
    }


    fun getAndClear(): T? {
        if (buffer.isEmpty()) {
            return null
        }

        val value = buffer[0]
        buffer.clear()
        return value
    }
}