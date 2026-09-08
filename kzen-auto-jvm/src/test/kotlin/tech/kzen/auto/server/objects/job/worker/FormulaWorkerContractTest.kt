package tech.kzen.auto.server.objects.job.worker

import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.common.data.model.DataUnit
import tech.kzen.auto.common.objects.document.job.FormulaCarrySpec
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelInputIterator
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.lib.common.exec.data.binding.BindingSchema
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.type.toDataContract
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.platform.ClassName
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull


class FormulaWorkerContractTest {
    private lateinit var context: KzenAutoContext


    @Before
    fun setUp() {
        context = KzenAutoContext.forTest()
    }


    @After
    fun tearDown() {
        context.close()
    }


    @Test
    fun nestedRecordsKeepTheirShapeAndOpaqueInputsUseSyntheticProjection() {
        val native = TypeMetadata(ClassName(DataUnit::class.qualifiedName!!), emptyList(), false)
        assertSynthetic(worker().payloadFlow(
            JobLaneDescriptor(native.toDataContract()), laneContext()), native)

        val nested = DataContract(
            DataType.Record(listOf(DataField(
                FieldId("nested"),
                DataType.Mapping(
                    DataType.Scalar(ScalarKind.Text),
                    DataType.Scalar(ScalarKind.Text))))),
            mapOf(DataTypePath.root to native))
        val output = worker().payloadFlow(JobLaneDescriptor(nested), laneContext())
        assertNull(output.errorMessage)
        val record = assertIs<DataType.Record>(output.lane.contract.structural)
        assertEquals(listOf("nested", "flatDate"), record.fields.map { it.id.name })
        assertIs<DataType.Mapping>(record.fields.first().type)
        assertEquals(native, output.lane.contract.nativeByPath[DataTypePath.root])
    }


    @Test
    fun nativeFieldMetadataSurvivesCalculatedColumnsAndChainedValidation() {
        tech.kzen.lib.common.exec.data.value.DefaultDataAdapterRegistry().use { registry ->
            val item = tech.kzen.auto.server.objects.job.value.RecordOverlayTest.Item(
                "2019-12-30", "AAPL", "source",
                tech.kzen.auto.server.objects.job.value.RecordOverlayTest.Day(true, emptyList(), emptyMap()), null)
            val contract = registry.lift(item).contract
            val first = worker("test", "symbol.length").payloadFlow(JobLaneDescriptor(contract), laneContext())
            assertNull(first.errorMessage)
            assertEquals(contract.nativeByPath, first.lane.contract.nativeByPath)
            assertEquals(ScalarKind.Integer(32),
                (first.lane.contract.child(tech.kzen.lib.common.exec.data.type.DataPathSegment.Field(FieldId("test"))).structural as DataType.Scalar).kind)
            val next = worker("checked", "test == symbol.length && day.open").payloadFlow(first.lane, laneContext())
            assertNull(next.errorMessage)
            assertEquals(7, (next.lane.contract.structural as DataType.Record).fields.size)
        }
    }


    private fun assertSynthetic(attempt: JobLaneAttempt, native: TypeMetadata) {
        assertNull(attempt.errorMessage)
        val record = assertIs<DataType.Record>(attempt.lane.contract.structural)
        assertEquals(listOf("value", "flatDate"), record.fields.map { it.id.name })
        assertEquals(
            listOf(ScalarKind.Text, ScalarKind.Text),
            record.fields.map { assertIs<DataType.Scalar>(it.type).kind })
        assertEquals(native, attempt.lane.contract.nativeByPath[DataTypePath.root])
    }


    private fun worker(name: String = "flatDate", expression: String = "\"2026-09-01\""): FormulaWorker = FormulaWorker(
        EmptyInput,
        IgnoredOutput,
        FormulaSpec(mapOf(name to expression)),
        "",
        FormulaCarrySpec.none,
        ObjectLocation.parse("test/formula-worker-contract.yaml#main.workers/formula"),
        context.jobExpressionCompiler)


    private fun laneContext(): JobLaneContext = JobLaneContext(
        BindingSchema.empty,
        GraphStructure.empty,
        FormulaWorker::class.java.classLoader)


    private object EmptyInput: ChannelInput<Any?> {
        override suspend fun receive(): Any? = null
        override suspend fun receiveBatch(): List<Any?>? = null
        override fun iterator(): ChannelInputIterator<Any?> = error("Not executed")
    }


    private object IgnoredOutput: ChannelOutput<DataValue> {
        override suspend fun send(element: DataValue) {}
        override suspend fun flush() {}
        override fun batchSize(): Int = 1
        override fun close() {}
    }
}
