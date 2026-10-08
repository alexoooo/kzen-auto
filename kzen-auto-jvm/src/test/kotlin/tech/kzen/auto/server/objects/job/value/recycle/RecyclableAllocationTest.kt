package tech.kzen.auto.server.objects.job.value.recycle

import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.server.objects.job.channel.FrameworkChannelInput
import tech.kzen.auto.server.objects.job.channel.JobChannel
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.lib.common.exec.data.value.DataValue
import java.lang.management.ManagementFactory
import kotlin.test.assertTrue


/**
 * WR2's machinery allocates nothing per element: a pool cycle (acquire, hold, release, recycle) allocates
 * nothing at all, and a pooled element costs no more through a channel and a framework batch than an ordinary
 * one does. Measured as this thread's allocated bytes; the numbers are printed for the session record.
 */
class RecyclableAllocationTest {
    private val elements = 200_000
    private val batchSize = 256
    private val rounds = 3

    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean


    @Test
    fun aPoolCycleAllocatesNothing() {
        val pool = RecyclablePool { PooledRecord(it) }
        repeat(rounds) { cycle(pool, elements) }
        val allocated = (1..rounds).minOf { allocatedBy { cycle(pool, elements) } }
        println("WR2 allocation: $allocated bytes for $elements pool cycles")
        assertTrue(allocated < elements, "$allocated bytes for $elements cycles")
    }


    @Test
    fun aPooledElementCostsNoMoreThroughAChannelThanAnOrdinaryOne() {
        val pool = RecyclablePool { PooledRecord(it) }
        val names = (0 until batchSize).map { "r$it" }
        val ordinary = (0 until batchSize).map { JobDataValues.lift(it.toLong()) }
        val ordinaryValue = { index: Int -> ordinary[index] }
        val pooledValue = { index: Int -> pool.acquire().fill(index.toLong(), names[index]) }
        repeat(rounds) {
            stream(ordinaryValue)
            stream(pooledValue)
        }
        val ordinaryBytes = (1..rounds).minOf { allocatedBy { stream(ordinaryValue) } }
        val pooledBytes = (1..rounds).minOf { allocatedBy { stream(pooledValue) } }
        println("WR2 allocation through a channel: ordinary $ordinaryBytes bytes, pooled $pooledBytes bytes " +
            "for $elements elements")
        assertTrue(pooledBytes <= ordinaryBytes + elements, "pooled $pooledBytes vs ordinary $ordinaryBytes")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun cycle(pool: RecyclablePool<PooledRecord>, count: Int) {
        repeat(count) {
            val record = pool.acquire()
            record.hold()
            record.token.release()
        }
    }


    // One batch at a time through an unbound channel and the framework receive path: send, flush, receive,
    // dispatch, release — no suspension, so everything runs on this thread
    private fun stream(value: (Int) -> DataValue) {
        val channel = JobChannel(capacity = 1, batchSize = batchSize)
        val input = channel.input as FrameworkChannelInput
        runBlocking {
            val producer = channel.newProducer()
            repeat(elements / batchSize) {
                for (index in 0 until batchSize) {
                    producer.send(value(index))
                }
                producer.flush()
                val batch = input.receiveFrameworkBatch()!!
                for (index in 0 until batch.size) {
                    batch.markDispatched(index)
                    batch.channelLease(index)?.release()
                }
            }
            producer.close()
        }
    }


    private fun allocatedBy(work: () -> Unit): Long {
        val thread = Thread.currentThread().threadId()
        val before = threads.getThreadAllocatedBytes(thread)
        work()
        return threads.getThreadAllocatedBytes(thread) - before
    }
}
