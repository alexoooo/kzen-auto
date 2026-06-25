package tech.kzen.auto.server.objects.job.channel

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Test
import kotlin.test.assertEquals


/**
 * Direct unit test of [JobChannel]'s migration carryover — no graph, no notation. A state migration tears the
 * running graph down and rebuilds it, so a channel's in-flight payloads (buffered, or one a producer is parked
 * mid-send on) must be snapshotted ([JobChannel.drainBuffered]) before teardown and re-seeded into the rebuilt
 * channel ([JobChannel.preload]), which then delivers that carryover ahead of the live stream — so the consumer
 * sees the exact same sequence across the cut, none dropped or duplicated.
 */
class JobChannelTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun drainCapturesBufferedPayloadsInOrder() = runBlocking {
        val channel = JobChannel(4)
        val producer = channel.newProducer()
        producer.send("a")
        producer.send("b")
        producer.send("c")

        assertEquals(listOf("a", "b", "c"), channel.drainBuffered())
        // A drained channel holds nothing more.
        assertEquals(listOf<Any?>(), channel.drainBuffered())
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun drainCapturesAPayloadParkedMidSend() = runBlocking {
        // Buffer 2 plus a third send that parks (channel full): the parked payload is NOT in the buffer, so it
        // is captured from the producer's in-flight slot and appended after the buffered payloads — it is the
        // last to enter the channel.
        val channel = JobChannel(2)
        val producer = channel.newProducer()
        val sender = launch {
            producer.send(1)
            producer.send(2)
            producer.send(3)  // parks here: the buffer already holds 1, 2
        }
        yield()  // let the sender fill the buffer and park on send(3)

        assertEquals(listOf(1, 2, 3), channel.drainBuffered())

        sender.cancel()
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun preloadDeliversCarryoverBeforeLiveStreamViaReceive() = runBlocking {
        val channel = JobChannel(4)
        channel.preload(listOf("x", "y"))
        val producer = channel.newProducer()
        producer.send("live1")
        producer.send("live2")
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
        // The framework consumer loops (Transform / Sink workers) iterate, so the iterator path must also drain
        // the carryover before the live channel.
        val channel = JobChannel(4)
        channel.preload(listOf("x", "y"))
        val producer = channel.newProducer()
        producer.send("live1")
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
        // The end-to-end migration shape: drain a channel holding buffered + parked-mid-send payloads, seed the
        // rebuilt channel with that carryover, then keep producing — the consumer reads the original stream
        // followed seamlessly by the new payloads, none dropped or duplicated.
        val source = JobChannel(2)
        val producer = source.newProducer()
        val sender = launch {
            producer.send(1)
            producer.send(2)
            producer.send(3)  // parks
        }
        yield()
        val carried = source.drainBuffered()
        sender.cancel()
        assertEquals(listOf(1, 2, 3), carried)

        val rebuilt = JobChannel(2)
        rebuilt.preload(carried)
        val rebuiltProducer = rebuilt.newProducer()
        val rebuiltSender = launch {
            rebuiltProducer.send(4)
            rebuiltProducer.send(5)
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
