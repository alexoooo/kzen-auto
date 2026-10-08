package tech.kzen.auto.server.objects.job.channel

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.server.exec.job.ownership.LeaseHolder
import tech.kzen.auto.server.exec.job.ownership.NativeIdentityRegistry
import tech.kzen.auto.server.exec.job.ownership.RunOwnershipLedger
import tech.kzen.auto.server.objects.job.value.recycle.PooledRecord
import tech.kzen.auto.server.objects.job.value.recycle.RecyclablePool
import tech.kzen.lib.common.exec.logic.run.model.LogicRunId
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue


/**
 * A pooled element through a [JobChannel] (WR2): its send takes one hold and carries the object's token in the
 * lease slot without flushing, and never reaches the ledger; every release path lets the hold go once, a raw
 * read forfeits, and a migration carries the hold to the next consumer.
 */
class JobChannelRecyclableTest {
    private val pool = RecyclablePool { PooledRecord(it) }


    private fun boundChannel(capacity: Int, batchSize: Int): Pair<JobChannel, RunOwnershipLedger> {
        val channel = JobChannel(capacity, batchSize)
        val ledger = RunOwnershipLedger(LogicRunId("recyclable"), NativeIdentityRegistry())
        channel.bindOwnership(ledger, LeaseHolder("channel"))
        return channel to ledger
    }


    private fun record(id: Long): PooledRecord =
        pool.acquire().also { it.fill(id, "r$id") }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun sendHoldsOnceAndCarriesTheTokenWithoutFlushingOrTheLedger() = runBlocking {
        val (channel, ledger) = boundChannel(capacity = 4, batchSize = 8)
        val record = record(1)
        val producer = channel.newProducer()
        producer.send(record.value)
        assertEquals(1, record.holds())
        assertEquals(0, channel.queuedElements(), "batched like an unowned element, not flushed at once")
        assertTrue(ledger.owners(record.value).isEmpty)

        producer.flush()
        val batch = (channel.input as FrameworkChannelInput).receiveFrameworkBatch()!!
        assertSame(record.token, batch.channelLease(0))
        batch.markDispatched(0)
        batch.channelLease(0)!!.release()
        assertEquals(1, record.generation)
    }


    @Test
    fun theSameObjectSentTwiceIsHeldTwice() = runBlocking {
        val (channel, _) = boundChannel(capacity = 4, batchSize = 8)
        val record = record(1)
        val producer = channel.newProducer()
        producer.send(record.value)
        producer.send(record.value)
        producer.flush()
        assertEquals(2, record.holds())
        val batch = (channel.input as FrameworkChannelInput).receiveFrameworkBatch()!!
        batch.channelLease(0)!!.release()
        assertEquals(0, record.generation)
        batch.channelLease(1)!!.release()
        assertEquals(1, record.generation)
    }


    @Test
    fun rawReadsForfeitAtHandOut() = runBlocking {
        val (channel, _) = boundChannel(capacity = 4, batchSize = 1)
        val records = (1L..3L).map(::record)
        val producer = channel.newProducer()
        for (record in records) {
            producer.send(record.value)
            producer.flush()
        }
        producer.close()

        assertSame(records[0].value, channel.input.receive())
        assertTrue(records[0].isForfeited)
        assertEquals(listOf(records[1].value), channel.input.receiveBatch())
        assertTrue(records[1].isForfeited)
        val iterator = channel.input.iterator()
        assertTrue(iterator.hasNext())
        assertSame(records[2].value, iterator.next())
        assertTrue(records[2].isForfeited)
        assertFalse(iterator.hasNext())
        assertTrue(records.all { it.holds() == 0 }, "each read's hold was let go at the next pull")
        assertTrue(records.all { it.generation == 0 })
    }


    @Test
    fun closingTheConsumerReleasesBufferedTokens() = runBlocking {
        val (channel, _) = boundChannel(capacity = 4, batchSize = 1)
        val records = (1L..2L).map(::record)
        val producer = channel.newProducer()
        for (record in records) {
            producer.send(record.value)
            producer.flush()
        }
        (channel.input as FrameworkChannelInput).closeConsumer()
        assertTrue(records.all { it.generation == 1 })
    }


    @Test
    fun aParkedFlushIsDrainedAndReleasedWhenTheConsumerCloses() = runBlocking {
        val (channel, _) = boundChannel(capacity = 0, batchSize = 4)
        val records = (1L..2L).map(::record)
        val producer = channel.newProducer()
        val sender = async(start = CoroutineStart.UNDISPATCHED) {
            for (record in records) {
                producer.send(record.value)
            }
            producer.flush()
        }
        assertTrue(records.all { it.holds() == 1 }, "parked mid-flush")
        (channel.input as FrameworkChannelInput).closeConsumer()
        assertTrue(records.all { it.generation == 1 })
        sender.await()
    }


    @Test
    fun aFlushAfterTheConsumerClosedReleasesItsBatch() = runBlocking {
        val (channel, _) = boundChannel(capacity = 4, batchSize = 4)
        val records = (1L..2L).map(::record)
        val producer = channel.newProducer()
        for (record in records) {
            producer.send(record.value)
        }
        (channel.input as FrameworkChannelInput).closeConsumer()
        assertFailsWith<DownstreamClosedException> { producer.flush() }
        assertTrue(records.all { it.generation == 1 })
    }


    @Test
    fun aSendAfterTheConsumerClosedNeitherHoldsNorRecycles() = runBlocking {
        val (channel, _) = boundChannel(capacity = 4, batchSize = 4)
        (channel.input as FrameworkChannelInput).closeConsumer()
        val record = record(1)
        assertFailsWith<DownstreamClosedException> { channel.newProducer().send(record.value) }
        assertEquals(0, record.holds())
        assertEquals(0, record.generation)
    }


    @Test
    fun migrationCarriesTheHoldsAndTheNextConsumerReleasesEachOnce() = runBlocking {
        val (channel, _) = boundChannel(capacity = 4, batchSize = 1)
        val records = (1L..2L).map(::record)
        val producer = channel.newProducer()
        for (record in records) {
            producer.send(record.value)
            producer.flush()
        }
        val carryover = channel.drainBuffered()
        assertTrue(records.all { it.holds() == 1 && it.generation == 0 })

        val (rebuilt, _) = boundChannel(capacity = 4, batchSize = 1)
        rebuilt.preload(carryover)
        rebuilt.newProducer().close()
        val input = rebuilt.input as FrameworkChannelInput
        for (record in records) {
            val batch = input.receiveFrameworkBatch()!!
            assertSame(record.token, batch.channelLease(0))
            batch.markDispatched(0)
            batch.channelLease(0)!!.release()
        }
        assertNull(input.receiveFrameworkBatch())
        assertTrue(records.all { it.generation == 1 })
    }


    @Test
    fun releasingADroppedCarryoverRecyclesItsRecords() = runBlocking {
        val (channel, _) = boundChannel(capacity = 4, batchSize = 1)
        val record = record(1)
        val producer = channel.newProducer()
        producer.send(record.value)
        producer.flush()
        channel.drainBuffered().release()
        assertEquals(1, record.generation)
        assertFalse(record.isForfeited)
    }
}
