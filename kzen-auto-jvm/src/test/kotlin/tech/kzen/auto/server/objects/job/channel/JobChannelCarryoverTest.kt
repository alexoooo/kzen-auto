package tech.kzen.auto.server.objects.job.channel

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals


/**
 * The lossless half of a Job state migration (pause / edit config / continue): [JobChannel.drainBuffered]
 * snapshots everything a channel still holds at the cut — buffered payloads PLUS any a producer is parked
 * mid-[send] on — and [JobChannel.preload] restores it into the rebuilt channel ahead of the live stream, so
 * the consumer sees the exact same sequence it would have without the migration.
 *
 * This drives that mechanism DIRECTLY and deterministically: a parked sender is created with
 * [CoroutineStart.UNDISPATCHED], which runs the send synchronously on the calling thread until it suspends on
 * the full channel — so "buffer full + one sender parked mid-send" is reached with no wall-clock wait. (The
 * former `JobMigrationCarryoverTest` reproduced the same state by free-running a 200k-row pipeline and racing
 * to pause it mid-stream before completion — inherently timing-dependent, and flaky on a fast machine where the
 * run finished before the pause landed. The end-to-end migrate path — that [JobExecution] drains + preloads
 * each channel across a rebuild without dropping or replaying a row — stays covered deterministically by
 * `JobStateMigrationTest.editingNonReaderConfigResumesReaderFromItsPosition`.)
 */
class JobChannelCarryoverTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun drainBufferedCapturesBufferedThenParkedSendInOrder() = runBlocking {
        val channel = JobChannel(buffer = 2)
        val producer = channel.newProducer()

        // Fill the buffer (capacity 2), then start a third send that CANNOT complete (buffer full, no consumer):
        // UNDISPATCHED runs it synchronously until it suspends inside channel.send, so it is deterministically
        // parked mid-send (its payload held in Producer.inFlight, NOT in the buffer) by the time launch returns.
        producer.send("a")
        producer.send("b")
        val parkedSend = launch(start = CoroutineStart.UNDISPATCHED) { producer.send("c") }

        // Delivery order: buffered payloads (FIFO) first, then the parked-mid-send payload (it would enter the
        // channel last), each exactly once.
        assertEquals(listOf<Any?>("a", "b", "c"), channel.drainBuffered())

        parkedSend.cancel()
    }


    @Test
    fun preloadDeliversCarryoverBeforeTheLiveStream() = runBlocking {
        val rebuilt = JobChannel(buffer = 4)
        rebuilt.preload(listOf("a", "b", "c"))

        val producer = rebuilt.newProducer()
        producer.send("d")
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
