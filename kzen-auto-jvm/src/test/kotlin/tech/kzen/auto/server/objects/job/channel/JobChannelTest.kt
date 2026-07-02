package tech.kzen.auto.server.objects.job.channel

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import kotlin.test.assertEquals


/**
 * Direct unit test of [JobChannel]'s migration carryover — no graph, no notation. Workers emit single ELEMENTS
 * ([ChannelOutput.send] buffers them; [ChannelOutput.flush] sends the buffer as one chunk), and a state
 * migration snapshots a channel's in-flight elements ([JobChannel.drainBuffered]) before teardown and re-seeds
 * them into the rebuilt channel ([JobChannel.preload]), which then delivers that carryover ahead of the live
 * stream — so the consumer sees the exact same element sequence across the cut, none dropped or duplicated.
 *
 * `chunk = 1` here so each [emit] (send + flush) is a one-element chunk, making the buffered / parked-mid-send
 * states easy to reason about element-by-element; the buffer capacity is then counted in one-element chunks.
 */
class JobChannelTest {
    // Emit one element as its own chunk (send buffers; flush sends the chunk, suspending under backpressure).
    private suspend fun ChannelOutput<Any?>.emit(element: Any?) {
        send(element)
        flush()
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun drainCapturesBufferedElementsInOrder() = runBlocking {
        val channel = JobChannel(buffer = 4, chunk = 1)
        val producer = channel.newProducer()
        producer.emit("a")
        producer.emit("b")
        producer.emit("c")

        assertEquals(listOf("a", "b", "c"), channel.drainBuffered())
        // A drained channel holds nothing more.
        assertEquals(listOf<Any?>(), channel.drainBuffered())
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun drainCapturesAnElementParkedMidSend() = runBlocking {
        // Buffer 2 chunks plus a third flush that parks (channel full): the parked chunk is NOT in the buffer, so
        // it is captured from the producer's in-flight slot and appended after the buffered elements — it is the
        // last to enter the channel. UNDISPATCHED runs the sender synchronously until it suspends on the full send.
        val channel = JobChannel(buffer = 2, chunk = 1)
        val producer = channel.newProducer()
        val sender = launch(start = CoroutineStart.UNDISPATCHED) {
            producer.emit(1)
            producer.emit(2)
            producer.emit(3)  // parks here: the buffer already holds chunks [1], [2]
        }

        assertEquals(listOf(1, 2, 3), channel.drainBuffered())

        sender.cancel()
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun preloadDeliversCarryoverBeforeLiveStreamViaReceive() = runBlocking {
        val channel = JobChannel(buffer = 4, chunk = 1)
        channel.preload(listOf("x", "y"))
        val producer = channel.newProducer()
        producer.emit("live1")
        producer.emit("live2")
        producer.close()

        val received = mutableListOf<Any?>()
        while (true) {
            received.add(channel.input.receive() ?: break)
        }
        assertEquals(listOf<Any?>("x", "y", "live1", "live2"), received)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun preloadDeliversCarryoverBeforeLiveStreamViaIterator() = runBlocking {
        // The framework consumer loops (Transform / Sink workers) drain chunks, so the carryover must precede the
        // live channel on that path too.
        val channel = JobChannel(buffer = 4, chunk = 1)
        channel.preload(listOf("x", "y"))
        val producer = channel.newProducer()
        producer.emit("live1")
        producer.close()

        val received = mutableListOf<Any?>()
        for (item in channel.input) {
            received.add(item)
        }
        assertEquals(listOf<Any?>("x", "y", "live1"), received)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun migrationRoundTripPreservesTheInFlightStream() = runBlocking {
        // The end-to-end migration shape: drain a channel holding buffered + parked-mid-send elements, seed the
        // rebuilt channel with that carryover, then keep producing — the consumer reads the original stream
        // followed seamlessly by the new elements, none dropped or duplicated.
        val source = JobChannel(buffer = 2, chunk = 1)
        val producer = source.newProducer()
        val sender = launch(start = CoroutineStart.UNDISPATCHED) {
            producer.emit(1)
            producer.emit(2)
            producer.emit(3)  // parks
        }
        val carried = source.drainBuffered()
        sender.cancel()
        assertEquals(listOf(1, 2, 3), carried)

        val rebuilt = JobChannel(buffer = 2, chunk = 1)
        rebuilt.preload(carried)
        val rebuiltProducer = rebuilt.newProducer()
        val rebuiltSender = launch {
            rebuiltProducer.emit(4)
            rebuiltProducer.emit(5)
            rebuiltProducer.close()
        }

        val received = mutableListOf<Any?>()
        for (item in rebuilt.input) {
            received.add(item)
        }
        rebuiltSender.join()
        assertEquals(listOf<Any?>(1, 2, 3, 4, 5), received)
    }
}
