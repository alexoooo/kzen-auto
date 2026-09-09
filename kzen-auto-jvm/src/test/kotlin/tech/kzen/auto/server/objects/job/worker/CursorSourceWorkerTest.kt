package tech.kzen.auto.server.objects.job.worker

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.obj.ObjectPath
import kotlin.test.*

class CursorSourceWorkerTest {
    private val location = ObjectLocation(DocumentPath.parse("test/cursor.yaml"), ObjectPath.parse("main"))

    @Test fun adoptedCursorIsReboundBeforePullAndClosesOnce() = runBlocking {
        val cursor = CountingCursor()
        val controls = mutableListOf<JobControl>()
        val emitted = mutableListOf<DataValue>()
        val output = object: ChannelOutput<DataValue> {
            override suspend fun send(element: DataValue) { emitted.add(element) }
            override suspend fun flush() {}
            override fun batchSize() = 1
            override fun close() {}
        }
        fun worker() = object: CursorSourceWorker(output, location) {
            override fun open(control: JobControl): Iterator<*> { cursor.opens++; return cursor }
            override fun onCursorReady(iterator: Iterator<*>, control: JobControl) {
                assertSame(cursor, iterator)
                controls.add(control)
            }
        }
        val first = worker()
        var captured: Any? = null
        var checkpoints = 0
        val outgoing = object: JobControl by NoOpControl {
            override suspend fun checkpoint() {
                if (++checkpoints == 2) {
                    captured = first.captureMigrationState()
                    throw CancellationException("migration")
                }
            }
        }
        try { first.run(outgoing); fail("must migrate") } catch (_: CancellationException) {}
        assertEquals(0, cursor.closes)
        val next = worker()
        next.loadMigrationState(captured)
        next.run(NoOpControl)
        assertEquals(1, cursor.opens)
        assertEquals(1, cursor.closes)
        assertEquals(3, emitted.size)
        assertEquals(listOf(outgoing, NoOpControl), controls)
    }

    private class CountingCursor: Iterator<String>, AutoCloseable {
        var opens = 0
        var closes = 0
        private var index = 0
        override fun hasNext() = index < 3
        override fun next() = (++index).toString()
        override fun close() { closes++ }
    }

    private object NoOpControl: JobControl {
        override suspend fun checkpoint() {}
        override suspend fun <R> runBlockingIo(block: () -> R): R = block()
        override fun scratchDir(): String = error("unused")
        override fun publishProgress(location: ObjectLocation, value: Map<String, Any?>, force: Boolean) {}
        override suspend fun host(instructions: ObjectLocation, input: Any?) = error("unused")
    }
}
