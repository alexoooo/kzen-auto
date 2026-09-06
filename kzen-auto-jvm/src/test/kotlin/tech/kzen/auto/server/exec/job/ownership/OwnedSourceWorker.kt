package tech.kzen.auto.server.exec.job.ownership

import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.objects.job.worker.Emitter
import tech.kzen.auto.server.objects.job.worker.SourceWorker
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit


/**
 * Test source emitting Worker-created closeables (the send-time adoption boundary of E9): each element is a
 * fresh [CloseCountingResource] lifted as a record. With [permits] set it models a host arena — every item
 * takes a permit that only its close gives back, taken inside the blocking offload — so a pipeline that kept an
 * owned item in a buffer would stall the source forever (the test's permit wait times out and fails instead).
 */
@Reflect
class OwnedSourceWorker(
    output: ChannelOutput<DataValue>,
    selfLocation: ObjectLocation
):
    SourceWorker(output, selfLocation)
{
    companion object {
        private const val permitTimeoutSeconds = 5L
        const val kindResource = "resource"
        const val kindRecord = "record"
        const val kindOpaque = "opaque"
        const val kindOrder = "order"

        @Volatile var names: List<String> = listOf("c", "b", "a")
        @Volatile var permits: Semaphore? = null
        /** What each element is: a [CloseCountingResource], a scalar-only [OwnedRecord], or an [OpaqueHandle]. */
        @Volatile var kind = kindResource
        val resources = mutableListOf<CloseCountingResource>()
        val records = mutableListOf<OwnedRecord>()
        val opaques = mutableListOf<OpaqueHandle>()
        val orders = mutableListOf<OwnedOrder>()

        fun reset() {
            names = listOf("c", "b", "a")
            permits = null
            kind = kindResource
            resources.clear()
            records.clear()
            opaques.clear()
            orders.clear()
        }
    }


    // Resumes rather than restarts across a live edit: the index is claimed before the send (a send parked
    // mid-flush is carried by the channel). @Volatile: read at the capture barrier.
    @Volatile
    private var nextIndex = 0


    override suspend fun produce(emit: Emitter, control: JobControl) {
        while (nextIndex < names.size) {
            val name = names[nextIndex]
            nextIndex += 1
            val arena = permits
            val element: Any = control.runBlockingIo {
                if (arena != null && !arena.tryAcquire(permitTimeoutSeconds, TimeUnit.SECONDS)) {
                    throw IllegalStateException("arena permit for $name not released: an owned item is stuck in a buffer")
                }
                when (kind) {
                    kindRecord -> OwnedRecord(name, names.indexOf(name)).also { synchronized(records) { records += it } }
                    kindOpaque -> OpaqueHandle().also { synchronized(opaques) { opaques += it } }
                    kindOrder -> order(name).also { synchronized(orders) { orders += it } }
                    else -> CloseCountingResource(name, onClose = { arena?.release() })
                        .also { synchronized(resources) { resources += it } }
                }
            }
            emit.send(JobDataValues.lift(element))
        }
    }


    // Two executions per order, priced by the order's position, so a projected total is checkable by a fold
    private fun order(name: String): OwnedOrder {
        val index = names.indexOf(name) + 1
        return OwnedOrder(name, listOf(
            OwnedOrder.Execution(10.0 * index, 5L * index),
            OwnedOrder.Execution(10.0 * index + 0.5, 3L)))
    }


    override fun captureMigrationState(): Any = nextIndex


    override fun loadMigrationState(captured: Any?) {
        nextIndex = captured as? Int ?: 0
    }
}
