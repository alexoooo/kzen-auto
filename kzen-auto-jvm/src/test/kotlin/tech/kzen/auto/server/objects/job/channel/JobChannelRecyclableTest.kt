package tech.kzen.auto.server.objects.job.channel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.server.objects.job.value.recycle.PooledRecord
import tech.kzen.auto.server.objects.job.value.recycle.RecyclablePool
import tech.kzen.lib.common.exec.engine.resource.Holder
import tech.kzen.lib.common.exec.engine.resource.ResourceRegistry
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue


/**
 * A pooled element through a [JobChannel] (WR2 over RH4): it is an owned element like any other — its send
 * takes one hold on the object's pool resource under the channel's holder and flushes at once; every release
 * path lets the hold go once, a raw read forfeits, and a migration carries the hold to the next consumer.
 */
class JobChannelRecyclableTest {
    private val pool = RecyclablePool { PooledRecord(it) }
    private val holder = Holder("channel")


    private fun boundChannel(capacity: Int, batchSize: Int): Pair<JobChannel, ResourceRegistry> {
        val channel = JobChannel(capacity, batchSize)
        val registry = ResourceRegistry()
        channel.bindResources(registry, holder)
        return channel to registry
    }


    private fun record(id: Long): PooledRecord =
        pool.acquire().also { it.fill(id, "r$id") }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun sendHoldsOnceUnderTheChannelsHolderAndFlushesAtOnce() = runBlocking {
        val (channel, registry) = boundChannel(capacity = 4, batchSize = 8)
        val record = record(1)
        val producer = channel.newProducer()
        producer.send(record.value)
        assertEquals(1, record.holds())
        assertEquals(mapOf(holder to 1), record.resource.holds())
        assertEquals(1, channel.queuedElements(), "an owned element is flushed at once")
        assertTrue(registry.live().isEmpty(), "a pooled object is its own resource, never adopted by the registry")

        val batch = (channel.input as FrameworkChannelInput).receiveFrameworkBatch()!!
        val hold = batch.channelHold(0)!!
        assertTrue(hold.isActive)
        batch.markDispatched(0)
        hold.release()
        assertEquals(1, record.generation)
        assertFalse(hold.isActive)
    }


    @Test
    fun theSameObjectSentTwiceIsHeldTwice() = runBlocking {
        val (channel, _) = boundChannel(capacity = 4, batchSize = 8)
        val record = record(1)
        val producer = channel.newProducer()
        producer.send(record.value)
        producer.send(record.value)
        assertEquals(2, record.holds())
        val input = channel.input as FrameworkChannelInput
        input.receiveFrameworkBatch()!!.channelHold(0)!!.release()
        assertEquals(0, record.generation)
        input.receiveFrameworkBatch()!!.channelHold(0)!!.release()
        assertEquals(1, record.generation)
    }


    @Test
    fun rawReadsForfeitAtHandOut() = runBlocking {
        val (channel, _) = boundChannel(capacity = 4, batchSize = 1)
        val records = (1L..3L).map(::record)
        val producer = channel.newProducer()
        for (record in records) {
            producer.send(record.value)
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
        assertTrue(records.all { it.holds() == 1 }, "each read's channel hold was let go at the next pull; the forfeit stays")
        assertTrue(records.all { it.generation == 0 })
    }


    @Test
    fun closingTheConsumerReleasesBufferedHolds() = runBlocking {
        val (channel, _) = boundChannel(capacity = 4, batchSize = 1)
        val records = (1L..2L).map(::record)
        val producer = channel.newProducer()
        for (record in records) {
            producer.send(record.value)
        }
        (channel.input as FrameworkChannelInput).closeConsumer()
        assertTrue(records.all { it.generation == 1 })
    }


    @Test
    fun aParkedSendIsDrainedAndReleasedWhenTheConsumerCloses() = runBlocking {
        val (channel, _) = boundChannel(capacity = 0, batchSize = 4)
        val record = record(1)
        val producer = channel.newProducer()
        val sender = async(start = CoroutineStart.UNDISPATCHED) {
            producer.send(record.value)
        }
        assertEquals(1, record.holds(), "parked mid-send")
        (channel.input as FrameworkChannelInput).closeConsumer()
        sender.await()
        assertEquals(1, record.generation)
    }


    @Test
    fun aReceiverCancelledAsItsBatchArrivedReleasesTheHolds() = runBlocking {
        val (channel, _) = boundChannel(capacity = 0, batchSize = 4)
        val record = record(1)
        val input = channel.input as FrameworkChannelInput
        val receiver = async(start = CoroutineStart.UNDISPATCHED) {
            input.receiveFrameworkBatch()
        }
        channel.newProducer().send(record.value)
        assertEquals(1, record.holds(), "handed to the parked receiver")
        receiver.cancel()
        assertFailsWith<CancellationException> { receiver.await() }
        assertEquals(1, record.generation, "a batch the cancelled receiver never saw is released by the transport")
    }


    @Test
    fun aSenderCancelledWhileParkedReleasesItsBatch() = runBlocking {
        val (channel, _) = boundChannel(capacity = 0, batchSize = 4)
        val record = record(1)
        val producer = channel.newProducer()
        val sender = async(start = CoroutineStart.UNDISPATCHED) {
            producer.send(record.value)
        }
        assertEquals(1, record.holds(), "parked mid-send")
        sender.cancel()
        assertFailsWith<CancellationException> { sender.await() }
        assertEquals(1, record.generation)
    }


    @Test
    fun aSenderCancelledAfterTheDrainCapturedItsBatchLeavesTheHoldsToTheCarryover() = runBlocking {
        val (channel, _) = boundChannel(capacity = 0, batchSize = 4)
        val record = record(1)
        val producer = channel.newProducer()
        val sender = async(start = CoroutineStart.UNDISPATCHED) {
            producer.send(record.value)
        }
        val carryover = channel.drainBuffered()
        sender.cancel()
        assertFailsWith<CancellationException> { sender.await() }
        assertEquals(1, record.holds(), "the carryover's")
        assertEquals(0, record.generation)
        carryover.release()
        assertEquals(1, record.generation)
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
        }
        val carryover = channel.drainBuffered()
        assertTrue(records.all { it.holds() == 1 && it.generation == 0 })

        val (rebuilt, _) = boundChannel(capacity = 4, batchSize = 1)
        rebuilt.preload(carryover)
        rebuilt.newProducer().close()
        val input = rebuilt.input as FrameworkChannelInput
        for (record in records) {
            val batch = input.receiveFrameworkBatch()!!
            assertSame(record.value, batch.elements[0])
            batch.markDispatched(0)
            batch.channelHold(0)!!.release()
            assertEquals(1, record.generation)
        }
        assertNull(input.receiveFrameworkBatch())
    }


    @Test
    fun releasingADroppedCarryoverRecyclesItsRecords() = runBlocking {
        val (channel, _) = boundChannel(capacity = 4, batchSize = 1)
        val record = record(1)
        val producer = channel.newProducer()
        producer.send(record.value)
        channel.drainBuffered().release()
        assertEquals(1, record.generation)
        assertFalse(record.isForfeited)
    }
}
