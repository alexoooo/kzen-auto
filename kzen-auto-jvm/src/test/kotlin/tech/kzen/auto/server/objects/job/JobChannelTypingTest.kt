package tech.kzen.auto.server.objects.job

import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.logic.LogicExecutionFacade
import tech.kzen.lib.common.exec.logic.LogicHandle
import tech.kzen.lib.common.exec.logic.model.LogicResultSuccess
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.server.exec.logic.context.MutableLogicControl
import tech.kzen.lib.server.exec.logic.context.MutableLogicResourceScope
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


/**
 * Phase-1 typed channels: a [tech.kzen.auto.server.objects.job.channel.JobChannel] declares an `elementType`,
 * and [tech.kzen.auto.common.objects.document.job.ChannelTypeDefiner] validates at definition time that every
 * referencing Worker port matches it and that the channel is single-reader. Proves the happy path still runs,
 * and that the two failure modes (producer/consumer type mismatch, multiple consumers) surface as definition
 * errors on the channel's `elementType` — the same `GraphDefinitionAttempt.failures` path the UI renders —
 * instead of a run-time ClassCastException on the framework's `item as In` cast.
 */
class JobChannelTypingTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val typedDocumentPath = DocumentPath.parse("test/job-typed-channel-test.yaml")
    private val mismatchDocumentPath = DocumentPath.parse("test/job-channel-type-mismatch-test.yaml")
    private val multiConsumerDocumentPath = DocumentPath.parse("test/job-channel-multi-consumer-test.yaml")

    private val elementTypeAttribute = AttributeName("elementType")

    private lateinit var context: KzenAutoContext


    //-----------------------------------------------------------------------------------------------------------------
    @Before
    fun setUp() {
        context = KzenAutoContext.forTest()
    }


    @After
    fun tearDown() {
        context.close()
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun typedChannelPipelineRuns() {
        // reader -> filter -> writer over fully-typed RecordBatch channels. Reaching Success (and the filtered
        // output) proves a typed channel defines + runs end-to-end — the declared elementType is wiring-only
        // metadata that does not perturb construction (JobChannel still takes only `buffer`) or execution.
        val dir = Path.of("build/job-typed-channel")
        Files.createDirectories(dir)
        Files.newBufferedWriter(dir.resolve("input.csv")).use {
            it.write("id,amount"); it.newLine()
            for (i in 0..9) {
                it.write("$i,$i"); it.newLine()
            }
        }
        Files.deleteIfExists(dir.resolve("output.csv"))

        val mainLocation = ObjectLocation(typedDocumentPath, ObjectPath.parse("main"))
        val execution = AutoTestUtils.liveLogicExecution(context, mainLocation, UnusedLogicHandle)
        execution.beforeStart(TupleValue.empty)

        val result = execution.continueOrStart(
            MutableLogicControl(false), MutableLogicResourceScope(), graphDefinition(typedDocumentPath))

        assertIs<LogicResultSuccess>(result)

        val lines = Files.readAllLines(dir.resolve("output.csv"))
        assertEquals("id,amount", lines.first())
        val amounts = lines.drop(1).map { it.split(",")[1].toInt() }
        assertEquals(listOf(3, 4, 5, 6, 7, 8, 9), amounts)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun producerTypeMismatchFailsDefinition() {
        // The channel declares it carries String; the reader's `output` is RecordBatch -> the channel's
        // elementType must fail to define, naming the mismatch.
        val attempt = AutoTestUtils.graphDefinitionAttempt(AutoTestUtils.readNotation())
        val rawChannel = ObjectLocation(mismatchDocumentPath, ObjectPath.parse("main.channels/raw"))

        val failure = attempt.failures[rawChannel]
        assertNotNull(failure, "type-mismatched channel should fail to define")

        val elementTypeError = failure.attributeErrors[elementTypeAttribute]
        assertNotNull(elementTypeError, "failure should be on the elementType attribute")
        assertTrue(
            elementTypeError.contains("carries"),
            "error should explain the type mismatch, was: $elementTypeError")
    }


    @Test
    fun multipleConsumersFailDefinition() {
        // Two Workers' `input` ports drain one channel -> the channel's elementType must fail to define on the
        // single-reader rule.
        val attempt = AutoTestUtils.graphDefinitionAttempt(AutoTestUtils.readNotation())
        val rawChannel = ObjectLocation(multiConsumerDocumentPath, ObjectPath.parse("main.channels/raw"))

        val failure = attempt.failures[rawChannel]
        assertNotNull(failure, "multi-consumer channel should fail to define")

        val elementTypeError = failure.attributeErrors[elementTypeAttribute]
        assertNotNull(elementTypeError, "failure should be on the elementType attribute")
        assertTrue(
            elementTypeError.contains("single-reader"),
            "error should explain the single-reader violation, was: $elementTypeError")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun graphDefinition(documentPath: DocumentPath): GraphDefinition {
        return AutoTestUtils.graphDefinitionAttempt(AutoTestUtils.readNotation())
            .transitiveSuccessful
            .filterTransitive(documentPath)
    }


    //-----------------------------------------------------------------------------------------------------------------
    // A Job's Workers communicate only over channels and never start a nested logic, so the handle is unused.
    private object UnusedLogicHandle: LogicHandle {
        override fun start(
            logicRunExecutionId: LogicRunExecutionId,
            originalObjectLocation: ObjectLocation
        ): LogicExecutionFacade =
            error("nested logic should not start for a Job")
    }
}
