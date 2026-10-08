package tech.kzen.auto.server.objects.job.value.recycle

import kotlinx.coroutines.awaitCancellation
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.objects.job.worker.SinkWorker
import tech.kzen.auto.server.objects.job.worker.ownership
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger


/**
 * Test sink reading every element it receives (a read of a recycled record fails by name) and recording what it
 * read. A pooled element must never have ledger owners; one that has is counted in [ledgerOwned]. With [gate]
 * the first instance never drains, so a live edit has values in flight to carry; [failAt] fails the callback on
 * that element (by arrival order). [note] is a no-op attribute a test edits to trigger a migration.
 */
@Reflect
class RecyclableSinkWorker(
    input: ChannelInput<*>,
    private val label: String,
    @Suppress("unused")
    private val note: String,
    private val gate: Boolean,
    private val failAt: Int,
    selfLocation: ObjectLocation
):
    SinkWorker(input, selfLocation)
{
    class Received(
        val sink: String,
        val value: Any?
    )


    companion object {
        val received = CopyOnWriteArrayList<Received>()
        val instances = AtomicInteger(0)
        val ledgerOwned = AtomicInteger(0)

        fun reset() {
            received.clear()
            instances.set(0)
            ledgerOwned.set(0)
        }

        fun of(sink: String): List<Any?> = received.filter { it.sink == sink }.map { it.value }
    }


    private var arrived = 0


    override suspend fun onStart(control: JobControl) {
        if (instances.incrementAndGet() == 1 && gate) {
            awaitCancellation()
        }
    }


    override suspend fun onElement(element: DataValue, control: JobControl) {
        if (Recyclable.of(element) != null && control.ownership()?.owners(element)?.isEmpty == false) {
            ledgerOwned.incrementAndGet()
        }
        val index = arrived
        arrived += 1
        if (index == failAt) {
            throw IllegalStateException("recycle fixture failure at $index")
        }
        received += Received(label, JobDataValues.native(element))
    }
}
