package tech.kzen.auto.server.objects.job.worker.content

import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.job.value.recycle.Recyclable
import tech.kzen.auto.server.objects.job.worker.Emitter
import tech.kzen.auto.server.objects.job.worker.TransformWorker
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicInteger


/**
 * Test pass-through ahead of `Write`: stamps each chunk's byte array with its pooled slot and the slot's generation,
 * and forwards the chunk. [intercept] wraps Write's encoder, so each write of a chunk is checked against its stamp:
 * one written while its slot is not held, or after the slot was recycled, counts in [usedAfterRecycle].
 */
@Reflect
class ChunkStampWorker(
    input: ChannelInput<*>,
    output: ChannelOutput<DataValue>,
    selfLocation: ObjectLocation
):
    TransformWorker(input, output, selfLocation)
{
    private class Stamp(
        val slot: Recyclable,
        val generation: Int
    )


    companion object {
        private val stamps: MutableMap<ByteArray, Stamp> = Collections.synchronizedMap(IdentityHashMap())

        val stamped = AtomicInteger(0)
        val checked = AtomicInteger(0)
        val unstamped = AtomicInteger(0)
        val usedAfterRecycle = AtomicInteger(0)

        fun reset() {
            stamps.clear()
            stamped.set(0)
            checked.set(0)
            unstamped.set(0)
            usedAfterRecycle.set(0)
        }


        fun intercept(encoded: OutputStream): OutputStream =
            object: FilterOutputStream(encoded) {
                override fun write(b: ByteArray, off: Int, len: Int) {
                    val stamp = stamps[b]
                    if (stamp == null) {
                        unstamped.incrementAndGet()
                    }
                    else if (stamp.slot.holds() == 0 || stamp.slot.generation != stamp.generation) {
                        usedAfterRecycle.incrementAndGet()
                    }
                    checked.incrementAndGet()
                    out.write(b, off, len)
                }
            }
    }


    override suspend fun onElement(element: DataValue, emit: Emitter, control: JobControl) {
        val slot = checkNotNull(Recyclable.of(element) as? PooledBytes) { "A chunk must be pooled" }
        stamps[slot.bytes.buffer().bytes] = Stamp(slot, slot.generation)
        stamped.incrementAndGet()
        emit.send(element)
    }
}
