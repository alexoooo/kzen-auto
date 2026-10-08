package tech.kzen.auto.server.objects.job.value.recycle

import org.junit.Test
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class RecyclablePoolTest {
    private val elements = 200_000
    private val queueCapacity = 64
    private val releasers = 4


    @Test
    fun reusesReleasedObjectsBeforeCreating() {
        val created = mutableListOf<PooledRecord>()
        val pool = RecyclablePool { PooledRecord(it).also(created::add) }
        val first = pool.acquire()
        val second = pool.acquire()
        first.hold()
        second.hold()
        first.release()
        second.release()
        val reused = setOf(pool.acquire(), pool.acquire())
        assertEquals(setOf(first, second), reused)
        assertEquals(2, pool.created)
        pool.acquire()
        assertEquals(3, created.size)
    }


    @Test
    fun concurrentReleasersNeverLoseNorDuplicateAnObject() {
        // The producer hands each held object to one of several releaser threads and keeps acquiring: an object
        // handed out again while still held, or recycled twice, would show a hold at acquire or an extra generation
        val created = mutableListOf<PooledRecord>()
        val pool = RecyclablePool { PooledRecord(it).also(created::add) }
        val queue = ArrayBlockingQueue<PooledRecord>(queueCapacity)
        val stillHeld = AtomicInteger(0)
        val done = PooledRecord(RecyclablePool { PooledRecord(it) })
        val workers = (1..releasers).map {
            thread {
                while (true) {
                    val record = queue.take()
                    if (record === done) {
                        break
                    }
                    record.release()
                }
            }
        }

        repeat(elements) {
            val record = pool.acquire()
            if (record.holds() != 0) {
                stillHeld.incrementAndGet()
            }
            record.hold()
            queue.put(record)
        }
        repeat(releasers) {
            queue.put(done)
        }
        workers.forEach { it.join(TimeUnit.SECONDS.toMillis(30)) }

        assertEquals(0, stillHeld.get(), "an object was handed out while held")
        assertEquals(elements, created.sumOf { it.generation }, "every release returned its object exactly once")
        // Outside the pool at once: the queue, one per releaser, and the producer's own
        assertTrue(pool.created <= queueCapacity + releasers + 1, "created ${pool.created}")
    }
}
