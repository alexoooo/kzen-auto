package tech.kzen.auto.server.objects.job.channel

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import kotlin.test.assertEquals


/**
 * The lossless half of a Job state migration (pause / edit config / continue): [JobChannel.drainBuffered]
 * snapshots everything a channel still holds at the cut — buffered elements PLUS any a producer is parked
 * mid-flush on — and [JobChannel.preload] restores it into the rebuilt channel ahead of the live stream, so the
 * consumer sees the exact same sequence it would have without the migration.
 *
 * Workers emit single ELEMENTS ([ChannelOutput.send] buffers; [ChannelOutput.flush] sends them as one batch);
 * `batchSize = 1` makes each [emit] a one-element batch so the buffered / parked states are element-precise. This
 * drives the mechanism DIRECTLY and deterministically: a parked sender is created with
 * [CoroutineStart.UNDISPATCHED], which runs the send synchronously on the calling thread until it suspends on
 * the full channel — so "channel full + one sender parked mid-flush" is reached with no wall-clock wait.
 */
class JobChannelCarryoverTest {
    private suspend fun ChannelOutput<Any?>.emit(element: Any?) {
        send(element)
        flush()
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun drainBufferedCapturesBufferedThenParkedSendInOrder() = runBlocking {
        val channel = JobChannel(capacity = 2, batchSize = 1)
        val producer = channel.newProducer()

        // Fill the buffer (capacity 2 batches), then start a third flush that CANNOT complete (channel full, no
        // consumer): UNDISPATCHED runs it synchronously until it suspends inside channel.send, so it is
        // deterministically parked mid-flush (its batch held in Producer.inFlight, NOT in the buffer).
        producer.emit("a")
        producer.emit("b")
        val parkedSend = launch(start = CoroutineStart.UNDISPATCHED) { producer.emit("c") }

        // Delivery order: buffered elements (FIFO) first, then the parked-mid-flush element (it would enter the
        // channel last), each exactly once.
        assertEquals(listOf<Any?>("a", "b", "c"), channel.drainBuffered())

        parkedSend.cancel()
    }


    @Test
    fun preloadDeliversCarryoverBeforeTheLiveStream() = runBlocking {
        val rebuilt = JobChannel(capacity = 4, batchSize = 1)
        rebuilt.preload(listOf("a", "b", "c"))

        val producer = rebuilt.newProducer()
        producer.emit("d")
        producer.close()

        val received = mutableListOf<Any?>()
        val iterator = rebuilt.input.iterator()
        while (iterator.hasNext()) {
            received.add(iterator.next())
        }

        // The carryover from the torn-down channel is delivered first, then the live stream — so the rebuilt
        // graph resumes the stream without a gap or reorder.
        assertEquals(listOf<Any?>("a", "b", "c", "d"), received)
    }
}
