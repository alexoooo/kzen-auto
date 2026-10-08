package tech.kzen.auto.server.objects.job.worker.format

import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.objects.job.value.recycle.Recyclable
import tech.kzen.auto.server.objects.job.worker.SinkWorker
import tech.kzen.auto.server.objects.job.worker.content.Bytes
import tech.kzen.auto.server.objects.job.worker.content.FileNameTemplate
import tech.kzen.auto.server.objects.job.worker.content.FileValues
import tech.kzen.auto.server.objects.job.worker.content.PooledBytes
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import java.io.ByteArrayOutputStream
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger


/**
 * Test sink copying each [Bytes] chunk inside its callback, as a writer must, and recording what it copied: the
 * chunk's `name`, its parent's `parent.name` (the file a record came from), and its bytes. A chunk that is not held
 * while it is read, or is recycled while it is copied, is counted in [usedAfterRecycle]; [slots] collects the pooled
 * objects seen, so a test can bound how many the pool created.
 */
@Reflect
class BytesSinkWorker(
    input: ChannelInput<*>,
    selfLocation: ObjectLocation
):
    SinkWorker(input, selfLocation)
{
    class Chunk(
        val name: String?,
        val file: String?,
        val bytes: ByteArray
    ) {
        val text: String
            get() = bytes.decodeToString()
    }


    companion object {
        val chunks = CopyOnWriteArrayList<Chunk>()
        val slots: MutableSet<Recyclable> = Collections.synchronizedSet(Collections.newSetFromMap(IdentityHashMap()))
        val usedAfterRecycle = AtomicInteger(0)

        fun reset() {
            chunks.clear()
            slots.clear()
            usedAfterRecycle.set(0)
        }

        fun text(): String =
            chunks.joinToString("") { it.text }
    }


    override suspend fun onElement(element: DataValue, control: JobControl) {
        val slot = checkNotNull(Recyclable.of(element)) { "A chunk must be pooled" }
        slots += slot
        val generation = slot.generation

        val bytes = JobDataValues.native(element) as Bytes
        check(bytes === (slot as PooledBytes).bytes) { "A chunk's Bytes must be handed out by identity" }
        val copy = ByteArrayOutputStream(bytes.length())
        bytes.writeTo(copy)

        if (slot.holds() == 0 || slot.generation != generation) {
            usedAfterRecycle.incrementAndGet()
        }
        val metadata = element.metadata?.value
        chunks += Chunk(
            FileNameTemplate.metadataText(metadata, FileValues.name),
            FileNameTemplate.metadataText(metadata, "${FileValues.parent}.${FileValues.parent}.${FileValues.name}"),
            copy.toByteArray())
    }
}
