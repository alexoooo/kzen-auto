package tech.kzen.auto.common.paradigm.flow.model.channel

import tech.kzen.auto.common.paradigm.flow.api.output.BatchOutput
import tech.kzen.auto.common.paradigm.flow.api.output.OptionalOutput
import tech.kzen.auto.common.paradigm.flow.api.output.RequiredOutput
import tech.kzen.auto.common.paradigm.flow.api.output.StreamOutput
import tech.kzen.lib.common.exec.data.type.DataContract


/**
 * The one channel behind all four declared output types. [kind] is the type the vertex declared, which is what
 * makes the "at most one item" half of the contract checkable here; [label] names the channel in the failure
 * message, since the channel itself has no location. The runner clears the buffer before each execution, so a
 * guard here reads per-execution.
 *
 * The other half — a [RequiredOutput] that emitted nothing — is checked by the runner after `process` returns,
 * because only the runner knows which vertices reach `process` at all.
 */
class MutableFlowOutput<T>(
    val kind: FlowOutputKind,
    private val label: String,
    override val contract: DataContract = DataContract(
        tech.kzen.lib.common.exec.data.type.DataType.Dynamic(nullable = true)),
    private val structural: Boolean = false
):
    OptionalOutput<T>,
    RequiredOutput<T>,
    BatchOutput<T>,
    StreamOutput<T>
{
    private val buffer = mutableListOf<T>()
    private var streamHasNext: Boolean = false


    override fun set(payload: T) {
        checkNotAlreadySet()
        buffer.add(payload)
        streamHasNext = false
    }


    override fun add(payload: T) {
        buffer.add(payload)
    }


    override fun set(payload: T, hasNext: Boolean) {
        checkNotAlreadySet()
        buffer.add(payload)
        this.streamHasNext = hasNext
    }


    private fun checkNotAlreadySet() {
        if (kind == FlowOutputKind.Batch) {
            return
        }

        check(buffer.isEmpty()) {
            "Output '$label' was already set: a $kind output emits at most one item per execution, " +
                    "use BatchOutput for multiple"
        }
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
