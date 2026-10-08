package tech.kzen.auto.server.objects.job.channel

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.lib.common.exec.data.value.DataValue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.assertEquals


/**
 * Direct unit test of [JobChannel]'s migration carryover and of the blocked count its deadlock monitor reads — no
 * graph, no notation. Workers emit single ELEMENTS
 * ([ChannelOutput.send] buffers them; [ChannelOutput.flush] sends the buffer as one batch), and a state
 * migration snapshots a channel's in-flight elements ([JobChannel.drainBuffered]) before teardown and re-seeds
 * them into the rebuilt channel ([JobChannel.preload]), which then delivers that carryover ahead of the live
 * stream — so the consumer sees the exact same element sequence across the cut, none dropped or duplicated.
 *
 * `batchSize = 1` here so each [emit] (send + flush) is a one-element batch, making the buffered / parked-mid-send
 * states easy to reason about element-by-element; the channel capacity is then counted in one-element batches.
 */
class JobChannelTest {
    private val awaitMillis = 10_000L


    // Emit one element as its own batch (send buffers; flush sends the batch, suspending under backpressure).
    private suspend fun ChannelOutput<DataValue>.emit(element: Any?) {
        send(JobDataValues.lift(element))
        flush()
    }


    private suspend fun awaitBlockedCount(channel: JobChannel, count: Int) {
        withTimeout(awaitMillis) {
            while (channel.blockedCount() != count) {
                delay(1)
            }
        }
    }


    // One thread a test can occupy, so a coroutine resumed on it waits to be dispatched as on a loaded machine
    private class StarvableThread: AutoCloseable {
        private val executor = Executors.newSingleThreadExecutor()
        val dispatcher = executor.asCoroutineDispatcher()
        private var release = CountDownLatch(0)

        // Returns once the thread is occupied, so whatever coroutine last ran on it has suspended
        fun occupy() {
            val occupied = CountDownLatch(1)
            val release = CountDownLatch(1)
            this.release = release
            executor.execute {
                occupied.countDown()
                release.await()
            }
            occupied.await()
        }

        fun free() {
            release.countDown()
        }

        override fun close() {
            free()
            dispatcher.close()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun drainCapturesBufferedElementsInOrder() = runBlocking {
        val channel = JobChannel(capacity = 4, batchSize = 1)
        val producer = channel.newProducer()
        producer.emit("a")
        producer.emit("b")
        producer.emit("c")

        assertEquals(listOf("a", "b", "c"), channel.drainBuffered().elements.map(JobDataValues::boundary))
        // A drained channel holds nothing more.
        assertEquals(listOf<DataValue>(), channel.drainBuffered().elements)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun drainCapturesAnElementParkedMidSend() = runBlocking {
        // Buffer 2 batches plus a third flush that parks (channel full): the parked batch is NOT in the buffer, so
        // it is captured from the producer's in-flight slot and appended after the buffered elements — it is the
        // last to enter the channel. UNDISPATCHED runs the sender synchronously until it suspends on the full send.
        val channel = JobChannel(capacity = 2, batchSize = 1)
        val producer = channel.newProducer()
        val sender = launch(start = CoroutineStart.UNDISPATCHED) {
            producer.emit(1)
            producer.emit(2)
            producer.emit(3)  // parks here: the buffer already holds batches [1], [2]
        }

        assertEquals(listOf(1, 2, 3), channel.drainBuffered().elements.map(JobDataValues::boundary))

        sender.cancel()
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun preloadDeliversCarryoverBeforeLiveStreamViaReceive() = runBlocking {
        val channel = JobChannel(capacity = 4, batchSize = 1)
        channel.preload(ChannelCarryover.of(listOf("x", "y").map(JobDataValues::lift)))
        val producer = channel.newProducer()
        producer.emit("live1")
        producer.emit("live2")
        producer.close()

        val received = mutableListOf<Any?>()
        while (true) {
            received.add(JobDataValues.boundary(channel.input.receive() ?: break))
        }
        assertEquals(listOf<Any?>("x", "y", "live1", "live2"), received)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun preloadDeliversCarryoverBeforeLiveStreamViaIterator() = runBlocking {
        // The framework consumer loops (Transform / Sink workers) drain batches, so the carryover must precede the
        // live channel on that path too.
        val channel = JobChannel(capacity = 4, batchSize = 1)
        channel.preload(ChannelCarryover.of(listOf("x", "y").map(JobDataValues::lift)))
        val producer = channel.newProducer()
        producer.emit("live1")
        producer.close()

        val received = mutableListOf<Any?>()
        for (item in channel.input) {
            received.add(JobDataValues.boundary(item))
        }
        assertEquals(listOf<Any?>("x", "y", "live1"), received)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun migrationRoundTripPreservesTheInFlightStream() = runBlocking {
        // The end-to-end migration shape: drain a channel holding buffered + parked-mid-send elements, seed the
        // rebuilt channel with that carryover, then keep producing — the consumer reads the original stream
        // followed seamlessly by the new elements, none dropped or duplicated.
        val source = JobChannel(capacity = 2, batchSize = 1)
        val producer = source.newProducer()
        val sender = launch(start = CoroutineStart.UNDISPATCHED) {
            producer.emit(1)
            producer.emit(2)
            producer.emit(3)  // parks
        }
        val carried = source.drainBuffered()
        sender.cancel()
        assertEquals(listOf(1, 2, 3), carried.elements.map(JobDataValues::boundary))

        val rebuilt = JobChannel(capacity = 2, batchSize = 1)
        rebuilt.preload(carried)
        val rebuiltProducer = rebuilt.newProducer()
        val rebuiltSender = launch {
            rebuiltProducer.emit(4)
            rebuiltProducer.emit(5)
            rebuiltProducer.close()
        }

        val received = mutableListOf<Any?>()
        for (item in rebuilt.input) {
            received.add(JobDataValues.boundary(item))
        }
        rebuiltSender.join()
        assertEquals(listOf<Any?>(1, 2, 3, 4, 5), received)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun aConsumerHandedABatchOrTheEndIsNotBlockedWhileItWaitsForItsThread() = runBlocking {
        // A woken endpoint still waiting for a thread can resume on its own, so it must not count toward a
        // deadlock verdict however long a loaded machine leaves it undispatched
        val channel = JobChannel(capacity = 0, batchSize = 1)
        val producer = channel.newProducer()
        StarvableThread().use { consumerThread ->
            val consumer = launch(consumerThread.dispatcher) {
                while (true) {
                    channel.input.receive() ?: break
                }
            }
            awaitBlockedCount(channel, 1)
            consumerThread.occupy()
            producer.emit("a")
            assertEquals(0, channel.blockedCount(), "handed a batch")

            consumerThread.free()
            awaitBlockedCount(channel, 1)
            consumerThread.occupy()
            producer.close()
            assertEquals(0, channel.blockedCount(), "handed the end of the stream")

            consumerThread.free()
            consumer.join()
        }
    }


    @Test
    fun aProducerWhoseBatchWasTakenIsNotBlockedWhileItWaitsForItsThread() = runBlocking {
        val channel = JobChannel(capacity = 0, batchSize = 1)
        val producer = channel.newProducer()
        StarvableThread().use { producerThread ->
            val sender = launch(producerThread.dispatcher) {
                producer.emit("a")
            }
            awaitBlockedCount(channel, 1)
            producerThread.occupy()
            assertEquals("a", JobDataValues.boundary(channel.input.receive()!!))
            assertEquals(0, channel.blockedCount())

            producerThread.free()
            sender.join()
        }
    }


    @Test
    fun endpointsNothingCanResumeStillCountAsBlocked() = runBlocking {
        // A consumer of a channel no producer ever opened, and a producer on a full channel nobody reads
        val orphan = JobChannel(capacity = 0, batchSize = 1)
        val reader = launch(start = CoroutineStart.UNDISPATCHED) {
            orphan.input.receive()
        }
        assertEquals(1, orphan.blockedCount())

        val unread = JobChannel(capacity = 1, batchSize = 1)
        val producer = unread.newProducer()
        val writer = launch(start = CoroutineStart.UNDISPATCHED) {
            producer.emit("a")
            producer.emit("b")
        }
        assertEquals(1, unread.blockedCount())

        reader.cancel()
        writer.cancel()
    }
}
