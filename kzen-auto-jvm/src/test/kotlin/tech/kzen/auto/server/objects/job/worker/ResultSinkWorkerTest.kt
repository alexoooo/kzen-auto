package tech.kzen.auto.server.objects.job.worker

import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelInputIterator
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.exec.tuple.TupleComponentDefinition
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import kotlin.test.AfterTest
import kotlin.test.assertEquals


/**
 * Unit test for [ResultSinkWorker]'s progress push — the wire contract [ResultWorkerDisplay]'s value box
 * renders from. Drives the sink's full [ResultSinkWorker.run] lifecycle over a fake [ChannelInput] of payload
 * [JobMessage]s and asserts the forced final push (WorkerBase.run, after onComplete) carries the settled kept
 * value's display text under [JobConventions.progressResultValueKey] (a single-element list so the generic
 * default-card status line skips it), keyed by [ResultSinkWorker.keep].
 */
class ResultSinkWorkerTest {
    //-----------------------------------------------------------------------------------------------------------------
    private lateinit var context: KzenAutoContext


    @AfterTest
    fun tearDown() {
        if (::context.isInitialized) {
            context.close()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun keepLastPushesTheFinalValue() = runBlocking {
        val pushes = runResultSink(ResultSinkWorker.last, listOf("a", "b", "c"))

        val (finalPush, finalForce) = pushes.last()
        assertEquals(true, finalForce, "the settled value lands on the forced final push")
        assertEquals(3L, finalPush["collected"])
        assertEquals(listOf("c"), finalPush[JobConventions.progressResultValueKey])
    }


    @Test
    fun keepFirstPushesTheFirstValue() = runBlocking {
        val pushes = runResultSink(ResultSinkWorker.first, listOf("a", "b", "c"))

        val (finalPush, _) = pushes.last()
        assertEquals(3L, finalPush["collected"])
        assertEquals(listOf("a"), finalPush[JobConventions.progressResultValueKey])
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun runResultSink(keep: String, payloads: List<Any?>): List<Pair<Map<String, Any?>, Boolean>> {
        context = KzenAutoContext.forTest()

        val selfLocation = ObjectLocation(
            DocumentPath.parse("test/result-sink-unit-test.yaml"),
            ObjectPath.parse("main.workers/collect"))

        // cachedKotlinCompiler is only used by the static payloadFlow validation, never the run path exercised
        // here — the forTest() service instance satisfies the ctor without being touched.
        val worker = ResultSinkWorker(
            singleBatchInput(payloads.map { JobMessage.ofPayload(it) }),
            "", keep, selfLocation, context.cachedKotlinCompiler)

        val control = RecordingJobControl()
        worker.run(control)
        return control.progressPushes
    }


    private fun singleBatchInput(messages: List<JobMessage>): ChannelInput<Any?> =
        object: ChannelInput<Any?> {
            // Hand the framework the whole stream as one batch, then EOF (mirrors SummaryWorkerTest.chunkedInput).
            private var served = false

            override suspend fun receiveBatch(): List<Any?>? {
                if (served) {
                    return null
                }
                served = true
                return messages
            }

            override suspend fun receive(): Any? = error("unused")

            override fun iterator(): ChannelInputIterator<Any?> = error("unused")
        }


    //-----------------------------------------------------------------------------------------------------------------
    // A ResultSink only consumes + checkpoints + publishes + yields; it reads the declared result signature at
    // onComplete, so results() declares `main: String`. Every push (value + force) is recorded, bypassing
    // EngineJobControl's throttle so the forced final push is always observable.
    private class RecordingJobControl: JobControl {
        val progressPushes = mutableListOf<Pair<Map<String, Any?>, Boolean>>()

        override suspend fun checkpoint() {}
        override suspend fun <R> runBlockingIo(block: () -> R): R = block()
        override fun scratchDir(): String =
            throw UnsupportedOperationException("A ResultSink needs no scratch dir")

        override fun publishProgress(location: ObjectLocation, value: Map<String, Any?>, force: Boolean) {
            progressPushes.add(value to force)
        }

        override fun results(): TupleDefinition =
            TupleDefinition(listOf(
                TupleComponentDefinition(TupleComponentName.main, LogicType(TypeMetadata.string))))

        override suspend fun host(instructions: ObjectLocation, input: Any?) =
            throw UnsupportedOperationException("A ResultSink hosts no child")
    }
}
