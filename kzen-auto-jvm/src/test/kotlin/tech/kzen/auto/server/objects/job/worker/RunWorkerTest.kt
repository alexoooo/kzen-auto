package tech.kzen.auto.server.objects.job.worker

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.data.model.DataUnit
import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelInputIterator
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.auto.server.exec.bindingSchemaOf
import tech.kzen.lib.common.exec.data.binding.BindingSchema
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.platform.ClassName
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull


class RunWorkerTest {
    private lateinit var context: KzenAutoContext
    private val child = ObjectLocation.parse("test/job/run/job-per-unit-child-test.yaml#main")
    private val self = ObjectLocation.parse("test/job/run/job-per-unit-test.yaml#main.workers/run")


    @AfterTest
    fun tearDown() {
        if (::context.isInitialized) context.close()
    }


    @Test
    fun validatesCompleteNamedArgumentsAcrossPayloadColumnsAndOuterParameters() {
        assertNull(attempt(validArguments()).errorMessage)
    }


    @Test
    fun validatesTypedPayloadMembersWhenTheLaneHasNoFlatPart() {
        val arguments = validArguments().toMutableMap().also {
            it["flatDate"] = "attributes[\"date\"]"
        }
        assertNull(attempt(arguments, HeaderListing.empty).errorMessage)
    }


    @Test
    fun rejectsMissingDuplicateUnknownAndMalformedArguments() {
        assertContains(attempt(validArguments() - "prefix").errorMessage.orEmpty(), "Missing Run argument 'prefix'")
        assertContains(attempt(validArguments() + ("unit" to "payload")).errorMessage.orEmpty(), "duplicates")
        assertContains(attempt(validArguments() + ("unknown" to "1")).errorMessage.orEmpty(), "Unknown Run argument")
        assertContains(attempt(validArguments() + ("prefix" to "if (")).errorMessage.orEmpty(), "prefix")
    }


    @Test
    fun runtimeRejectsMissingJobArgumentsBeforeHostEvenWhenStaticValidationIsBypassed() = runBlocking {
        if (!::context.isInitialized) context = KzenAutoContext.forTest()
        val missing = validArguments() - "prefix"
        val input = SingleInput(tech.kzen.auto.server.objects.job.value.JobDataValues.lift(DataUnit.of()))
        val output = CapturingOutput()
        val worker = RunWorker(input, output, child, missing, self, context.jobExpressionCompiler)

        // Resolve the exact child signature as the run compiler does, then deliberately continue despite the
        // payloadFlow error to prove the runtime boundary independently enforces Job completeness.
        assertContains(worker.payloadFlow(lane(), laneContext()).errorMessage.orEmpty(), "Missing Run argument")
        val control = RuntimeControl()
        val failure = assertFailsWith<IllegalArgumentException> { worker.run(control) }
        assertContains(failure.message.orEmpty(), "Missing Run argument 'prefix'")
        assertEquals(0, control.hostCalls)
    }


    private fun validArguments(): Map<String, String> = linkedMapOf(
        "outputDate" to "attributes[\"date\"]",
        "flatDate" to "flatDate",
        "prefix" to "prefix")


    private fun attempt(
        arguments: Map<String, String>,
        flatColumns: HeaderListing = HeaderListing.ofUnique(listOf("flatDate"))
    ): JobLaneAttempt {
        if (!::context.isInitialized) {
            context = KzenAutoContext.forTest()
        }
        val graph = AutoTestUtils.graphDefinitionAttempt(AutoTestUtils.readNotation()).transitiveSuccessful.graphStructure
        return RunWorker(
            EmptyInput, EmptyOutput, child, arguments, self, context.jobExpressionCompiler)
            .payloadFlow(lane(flatColumns), JobLaneContext(parameters(), graph, RunWorker::class.java.classLoader))
    }


    private fun lane(flatColumns: HeaderListing = HeaderListing.ofUnique(listOf("flatDate"))): JobLaneDescriptor = JobLaneDescriptor(
        TypeMetadata(ClassName(DataUnit::class.qualifiedName!!), emptyList(), false),
        flatColumns)


    private fun parameters(): BindingSchema = bindingSchemaOf("prefix" to TypeMetadata.string)


    private fun laneContext(): JobLaneContext {
        val graph = AutoTestUtils.graphDefinitionAttempt(AutoTestUtils.readNotation())
            .transitiveSuccessful.graphStructure
        return JobLaneContext(parameters(), graph, RunWorker::class.java.classLoader)
    }


    private object EmptyInput: ChannelInput<Any?> {
        override suspend fun receiveBatch(): List<Any?>? = null
        override suspend fun receive(): Any? = error("unused")
        override fun iterator(): ChannelInputIterator<Any?> = error("unused")
    }


    private object EmptyOutput: ChannelOutput<Any?> {
        override suspend fun send(element: Any?) = error("unused")
        override suspend fun flush() {}
        override fun batchSize(): Int = 1
        override fun close() {}
    }


    private class SingleInput(private val value: Any?): ChannelInput<Any?> {
        private var delivered = false

        override suspend fun receiveBatch(): List<Any?>? {
            if (delivered) return null
            delivered = true
            return listOf(value)
        }

        override suspend fun receive(): Any? = error("unused")
        override fun iterator(): ChannelInputIterator<Any?> = error("unused")
    }


    private class CapturingOutput: ChannelOutput<Any?> {
        override suspend fun send(element: Any?) = error("host must fail before send")
        override suspend fun flush() {}
        override fun batchSize(): Int = 1
        override fun close() {}
    }


    private class RuntimeControl: JobControl {
        var hostCalls = 0

        override suspend fun checkpoint() {}
        override suspend fun <R> runBlockingIo(block: () -> R): R = block()
        override fun scratchDir(): String = error("unused")
        override fun publishProgress(location: ObjectLocation, value: Map<String, Any?>, force: Boolean) {}
        override suspend fun host(
            instructions: ObjectLocation,
            input: Any?
        ): tech.kzen.lib.common.exec.data.binding.DataBindings {
            hostCalls += 1
            error("host must not be reached")
        }
    }
}
