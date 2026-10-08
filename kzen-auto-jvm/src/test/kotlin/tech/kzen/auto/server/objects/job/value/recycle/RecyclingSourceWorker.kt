package tech.kzen.auto.server.objects.job.value.recycle

import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.job.worker.Emitter
import tech.kzen.auto.server.objects.job.worker.SourceWorker
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger


/**
 * Test source emitting [count] pooled records from its own [RecyclablePool]: record `i` has id `i` and a name
 * that sorts in the reverse order of the ids. Every record any instance created is kept in [created], so a test
 * can sum the generations (each is one return to a pool) and compare against [acquired].
 */
@Reflect
class RecyclingSourceWorker(
    output: ChannelOutput<DataValue>,
    private val count: Int,
    selfLocation: ObjectLocation
):
    SourceWorker(output, selfLocation)
{
    companion object {
        val created = CopyOnWriteArrayList<PooledRecord>()
        val pools = CopyOnWriteArrayList<RecyclablePool<PooledRecord>>()
        val acquired = AtomicInteger(0)

        fun reset() {
            created.clear()
            pools.clear()
            acquired.set(0)
        }

        fun name(id: Int, count: Int): String =
            "n" + (count - id).toString().padStart(6, '0')
    }


    private val pool = RecyclablePool { PooledRecord(it).also(created::add) }
        .also(pools::add)

    // Resumes rather than restarts across a live edit: the index is claimed before the send (a send parked
    // mid-flush is carried by the channel). @Volatile: read at the capture barrier.
    @Volatile
    private var nextIndex = 0


    override suspend fun produce(emit: Emitter, control: JobControl) {
        while (nextIndex < count) {
            val id = nextIndex
            nextIndex += 1
            val record = pool.acquire()
            acquired.incrementAndGet()
            emit.send(record.fill(id.toLong(), name(id, count)))
        }
    }


    override fun captureMigrationState(): Any = nextIndex


    override fun loadMigrationState(captured: Any?) {
        nextIndex = captured as? Int ?: 0
    }
}
