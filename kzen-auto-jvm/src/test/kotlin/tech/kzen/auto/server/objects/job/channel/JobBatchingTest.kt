package tech.kzen.auto.server.objects.job.channel

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.job.worker.Emitter
import tech.kzen.lib.common.model.location.ObjectLocation
import kotlin.test.assertEquals


/**
 * Batching is a general, domain-agnostic Channel-framework capability: a Worker emits single ELEMENTS and the
 * framework groups them into chunks of the channel's configured `chunk` size for transfer (the old per-worker
 * `RecordBatch` hack is gone), so even the untyped scalar lane batches. This drives an [Emitter] with the SOURCE
 * cadence over a real [JobChannel] and asserts the consumer receives the elements grouped into chunk-sized
 * physical chunks — the configured size honoured, with a trailing partial chunk for the remainder.
 */
class JobBatchingTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun sourceCadenceGroupsScalarElementsIntoConfiguredChunks() = runBlocking {
        val channel = JobChannel(buffer = 8, chunk = 3)
        val producer = channel.newProducer()

        val emitter = Emitter<Any?>(producer)
        emitter.sourceCadence(NoOpControl) {}

        val sender = launch {
            for (i in 0 until 7) {
                emitter.send(i)
            }
            emitter.flush()   // trailing partial chunk, exactly as SourceWorker flushes after produce returns
            producer.close()
        }

        val chunks = mutableListOf<List<Any?>>()
        while (true) {
            chunks.add(channel.input.receiveChunk() ?: break)
        }
        sender.join()

        // 7 scalar elements at chunk size 3 → full chunks [0,1,2], [3,4,5], then the trailing [6].
        assertEquals(listOf<List<Any?>>(listOf(0, 1, 2), listOf(3, 4, 5), listOf(6)), chunks)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private object NoOpControl: JobControl {
        override suspend fun checkpoint() {}
        override suspend fun <R> runBlockingIo(block: () -> R): R = block()
        override fun publishProgress(location: ObjectLocation, value: Map<String, Any?>, force: Boolean) {}
        override suspend fun host(instructions: ObjectLocation, input: Any?) =
            throw UnsupportedOperationException("no child")
    }
}
