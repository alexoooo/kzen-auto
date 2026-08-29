package tech.kzen.auto.server.objects.job.worker

import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelInputIterator
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.common.objects.document.logic.BindingSignatureDefiner
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.lib.common.exec.data.binding.BindingDefinition
import tech.kzen.lib.common.exec.data.binding.BindingName
import tech.kzen.lib.common.exec.data.binding.BindingSchema
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.platform.ClassName
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertNull


/**
 * Unit test for [ResultSinkWorker]'s progress push — the wire contract [ResultWorkerDisplay]'s value box
 * renders from. Drives the sink's full [ResultSinkWorker.run] lifecycle over a fake [ChannelInput] of payload
 * values and asserts the forced final push (WorkerBase.run, after onComplete) carries the settled kept
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


    @Test
    fun keepAllYieldsEveryBoundaryValueInOrderIncludingEmpty() = runBlocking {
        context = KzenAutoContext.forTest()
        val selfLocation = ObjectLocation(
            DocumentPath.parse("test/result-sink-unit-test.yaml"),
            ObjectPath.parse("main.workers/collect"))
        val control = RecordingJobControl(listOfType(TypeMetadata.int))
        ResultSinkWorker(
            singleBatchInput(listOf(1, 2, 3).map(JobDataValues::lift)),
            "", ResultSinkWorker.all, selfLocation, context.cachedKotlinCompiler).run(control)
        assertEquals(listOf(1, 2, 3), control.yielded["main"])

        val emptyControl = RecordingJobControl(listOfType(TypeMetadata.int))
        ResultSinkWorker(
            singleBatchInput(emptyList<DataValue>()), "", ResultSinkWorker.all,
            selfLocation, context.cachedKotlinCompiler).run(emptyControl)
        assertEquals(emptyList<Any?>(), emptyControl.yielded["main"])
    }


    @Test
    fun keepAllMigratesByDefensiveCopyOnlyIntoTheSameMode() = runBlocking {
        context = KzenAutoContext.forTest()
        val selfLocation = ObjectLocation(
            DocumentPath.parse("test/result-sink-unit-test.yaml"),
            ObjectPath.parse("main.workers/collect"))
        val first = ResultSinkWorker(
            singleBatchInput(listOf(JobDataValues.lift(1))),
            "", ResultSinkWorker.all, selfLocation, context.cachedKotlinCompiler)
        first.run(RecordingJobControl(listOfType(TypeMetadata.int)))
        val captured = first.captureMigrationState()

        val resumedControl = RecordingJobControl(listOfType(TypeMetadata.int))
        ResultSinkWorker(
            singleBatchInput(listOf(JobDataValues.lift(2), JobDataValues.lift(3))),
            "", ResultSinkWorker.all, selfLocation, context.cachedKotlinCompiler)
            .also { it.loadMigrationState(captured) }
            .run(resumedControl)
        assertEquals(listOf(1, 2, 3), resumedControl.yielded["main"])

        val changedControl = RecordingJobControl(TypeMetadata.int)
        ResultSinkWorker(
            singleBatchInput(listOf(JobDataValues.lift(9))),
            "", ResultSinkWorker.last, selfLocation, context.cachedKotlinCompiler)
            .also { it.loadMigrationState(captured) }
            .run(changedControl)
        assertEquals(9, changedControl.yielded["main"])
    }


    @Test
    fun keepAllPayloadFlowValidatesTheMaterializedListElementType() {
        context = KzenAutoContext.forTest()
        val graph = AutoTestUtils.graphDefinitionAttempt(AutoTestUtils.readNotation())
            .transitiveSuccessful.graphStructure
        val selfLocation = ObjectLocation.parse(
            "test/job/run/job-per-unit-test.yaml#main.workers/outputs")
        val worker = ResultSinkWorker(
            singleBatchInput(emptyList()), "outputs", ResultSinkWorker.all,
            selfLocation, context.cachedKotlinCompiler)
        val dataRefType = TypeMetadata(
            ClassName(DataRef::class.qualifiedName!!), emptyList(), false)

        val attempt = worker.payloadFlow(
            JobLaneDescriptor(dataRefType, HeaderListing.empty),
            JobLaneContext(BindingSchema.empty, graph, ResultSinkWorker::class.java.classLoader))

        assertNull(attempt.errorMessage)
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
            singleBatchInput(payloads.map(JobDataValues::lift)),
            "", keep, selfLocation, context.cachedKotlinCompiler)

        val control = RecordingJobControl()
        worker.run(control)
        return control.progressPushes
    }


    private fun singleBatchInput(messages: List<DataValue>): ChannelInput<Any?> =
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
    private class RecordingJobControl(
        private val resultType: TypeMetadata = TypeMetadata.string
    ): JobControl {
        val progressPushes = mutableListOf<Pair<Map<String, Any?>, Boolean>>()
        val yielded = linkedMapOf<String, Any?>()

        override suspend fun checkpoint() {}
        override suspend fun <R> runBlockingIo(block: () -> R): R = block()
        override fun scratchDir(): String =
            throw UnsupportedOperationException("A ResultSink needs no scratch dir")

        override fun publishProgress(location: ObjectLocation, value: Map<String, Any?>, force: Boolean) {
            progressPushes.add(value to force)
        }

        override fun results(): BindingSchema =
            BindingSchema.of(BindingDefinition(
                BindingName("main"), BindingSignatureDefiner.contract(resultType)))

        override fun yieldResult(component: String, value: DataValue) {
            yielded[component] = JobDataValues.boundary(value)
        }

        override suspend fun host(instructions: ObjectLocation, input: Any?) =
            throw UnsupportedOperationException("A ResultSink hosts no child")
    }


    private fun listOfType(element: TypeMetadata): TypeMetadata =
        TypeMetadata(tech.kzen.lib.platform.ClassNames.kotlinList, listOf(element), false)
}
