package tech.kzen.auto.server.objects.job.worker

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.common.data.model.DataUnit
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelInputIterator
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.lib.common.exec.data.binding.BindingSchema
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.common.exec.data.type.DefinitionId
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.MetadataContract
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.type.toDataContract
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.DefaultDataAdapterRegistry
import tech.kzen.lib.common.exec.data.value.LiteralDataValues
import tech.kzen.lib.common.exec.data.value.ValueMetadata
import tech.kzen.lib.common.exec.data.value.recordOf
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.platform.ClassName
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
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
    fun calculatedFieldsBecomeMetadataAndThePayloadIsKept() {
        val native = TypeMetadata(ClassName(DataUnit::class.qualifiedName!!), emptyList(), false)
        val opaque = native.toDataContract()
        val opaqueOutput = worker().payloadFlow(JobLaneDescriptor(opaque), laneContext())
        assertNull(opaqueOutput.errorMessage)
        assertEquals(opaque, opaqueOutput.lane.contract.payload())
        assertMetadata(listOf("flatDate" to ScalarKind.Text), opaqueOutput)

        val nested = DataContract(
            DataType.Record(listOf(DataField(
                FieldId("nested"),
                DataType.Mapping(
                    DataType.Scalar(ScalarKind.Text),
                    DataType.Scalar(ScalarKind.Text))))),
            mapOf(DataTypePath.root to native))
        val output = worker().payloadFlow(JobLaneDescriptor(nested), laneContext())
        assertNull(output.errorMessage)
        assertEquals(nested, output.lane.contract.payload())
        assertMetadata(listOf("flatDate" to ScalarKind.Text), output)
    }


    @Test
    fun laterFormulasSeeEarlierMetadataByBareName() {
        DefaultDataAdapterRegistry().use { registry ->
            val contract = registry.lift(Item("2019-12-30", "AAPL", Day(true))).contract
            val first = worker("test", "symbol.length").payloadFlow(JobLaneDescriptor(contract), laneContext())
            assertNull(first.errorMessage)
            assertEquals(contract, first.lane.contract.payload())
            assertMetadata(listOf("test" to ScalarKind.Integer(32)), first)

            val next = worker("checked", "test == symbol.length && day.open && meta.test > 0")
                .payloadFlow(first.lane, laneContext())
            assertNull(next.errorMessage)
            assertNull(next.warningMessage)
            assertEquals(contract, next.lane.contract.payload())
            assertMetadata(listOf("test" to ScalarKind.Integer(32), "checked" to ScalarKind.Boolean), next)
        }
    }


    @Test
    fun aBareNameTheMetadataSharesWithThePayloadResolvesToThePayloadWithAWarning() {
        DefaultDataAdapterRegistry().use { registry ->
            val contract = registry.lift(Item("2019-12-30", "AAPL", Day(true))).contract
            val first = worker("symbol", "\"MSFT\"").payloadFlow(JobLaneDescriptor(contract), laneContext())
            val shadowed = worker("same", "symbol == meta.symbol").payloadFlow(first.lane, laneContext())
            assertNull(shadowed.errorMessage)
            assertEquals("same: 'symbol' is the payload's; the metadata's is meta.symbol", shadowed.warningMessage)

            val explicit = worker("same", "this.symbol == meta.symbol").payloadFlow(first.lane, laneContext())
            assertNull(explicit.warningMessage)
        }
    }


    @Test
    fun aDynamicPayloadStillHasItsCalculatedMetadataNamed() {
        val output = worker().payloadFlow(JobLaneDescriptor.unknown, laneContext())
        assertNull(output.errorMessage)
        assertIs<DataType.Dynamic>(output.lane.contract.structural)
        val metadata = assertIs<DataType.Record>(output.lane.contract.metadata!!.structural)
        assertEquals(listOf("flatDate"), metadata.fields.map { it.id.name })
        assertIs<DataType.Dynamic>(metadata.fields.single().type)
    }


    @Test
    fun recursiveMetadataHasItsCalculatedTypeBeforeTheRun() {
        val design = worker("grandparent", grandparent).payloadFlow(JobLaneDescriptor(lineageInput), laneContext())

        assertNull(design.errorMessage)
        val metadata = assertNotNull(design.lane.contract.metadata)
        assertEquals(
            listOf(FieldId("origin") to DataType.Reference(nodeId), FieldId("grandparent") to text),
            metadata.structural.fields.map { it.id to it.type })
        assertEquals(lineage.definitions, metadata.definitions)
    }


    @Test
    fun recursiveMetadataIsReadThroughTwoLevelsDuringTheRun() = runBlocking {
        val a = recordOf("name" to "a", "parent" to null)
        val b = recordOf("name" to "b", "parent" to a)
        val rows = listOf(
            recordOf("origin" to recordOf("name" to "c", "parent" to b)),
            recordOf("origin" to b)
        ).map { metadata ->
            LiteralDataValues.lift(recordOf("amount" to "1"), lineagePayload)
                .withMetadata(ValueMetadata.of(LiteralDataValues.lift(metadata, lineage.contract)))
        }
        val output = CollectingOutput()
        FormulaWorker(
            ListInput(rows),
            output,
            FormulaSpec(mapOf("grandparent" to grandparent)),
            "",
            ObjectLocation.parse("test/formula-worker-contract.yaml#main.workers/formula"),
            context.jobExpressionCompiler
        ).run(DeclaredInputControl(lineageInput))

        assertEquals(listOf("a", "none"), output.sent.map { value ->
            val metadata = checkNotNull(value.metadata)
            metadata.access.readText(metadata.access.field(metadata.root, FieldId("grandparent")))
        })
    }


    private fun assertMetadata(expected: List<Pair<String, ScalarKind>>, attempt: JobLaneAttempt) {
        val metadata = assertIs<DataType.Record>(attempt.lane.contract.metadata!!.structural)
        assertEquals(expected, metadata.fields.map { it.id.name to assertIs<DataType.Scalar>(it.type).kind })
    }


    private val text = DataType.Scalar(ScalarKind.Text)
    private val nodeId = DefinitionId("test.Node")

    // `origin: Node` with `Node(name: String, parent: Node?)`
    private val lineage = MetadataContract(
        DataType.Record(listOf(DataField(FieldId("origin"), DataType.Reference(nodeId)))),
        mapOf(nodeId to DataType.Record(listOf(
            DataField(FieldId("name"), text),
            DataField(FieldId("parent"), DataType.Reference(nodeId, nullable = true))))))
    private val lineagePayload = DataContract(DataType.Record(listOf(DataField(FieldId("amount"), text))))
    private val lineageInput = lineagePayload.withMetadata(lineage)
    private val grandparent = "origin.parent?.parent?.name ?: \"none\""


    data class Item(val date: String, val symbol: String, val day: Day)
    data class Day(val open: Boolean)


    private fun worker(name: String = "flatDate", expression: String = "\"2026-09-01\""): FormulaWorker = FormulaWorker(
        EmptyInput,
        IgnoredOutput,
        FormulaSpec(mapOf(name to expression)),
        "",
        ObjectLocation.parse("test/formula-worker-contract.yaml#main.workers/formula"),
        context.jobExpressionCompiler)


    private fun laneContext(): JobLaneContext = JobLaneContext(
        BindingSchema.empty,
        GraphStructure.empty,
        FormulaWorker::class.java.classLoader)


    private class ListInput(private val rows: List<DataValue>): ChannelInput<Any?> {
        private var delivered = false

        override suspend fun receiveBatch(): List<Any?>? =
            if (delivered) null else rows.also { delivered = true }

        override suspend fun receive(): Any? = error("unused")
        override fun iterator(): ChannelInputIterator<Any?> = error("unused")
    }


    private class CollectingOutput: ChannelOutput<DataValue> {
        val sent = mutableListOf<DataValue>()

        override suspend fun send(element: DataValue) {
            sent += element
        }

        override suspend fun flush() {}
        override fun batchSize(): Int = 1
        override fun close() {}
    }


    private class DeclaredInputControl(private val contract: DataContract): JobControl {
        override suspend fun checkpoint() {}
        override suspend fun <R> runBlockingIo(block: () -> R): R = block()
        override fun scratchDir(): String = error("unused")
        override fun publishProgress(location: ObjectLocation, value: Map<String, Any?>, force: Boolean) {}
        override fun inputContract(): DataContract = contract
        override suspend fun host(instructions: ObjectLocation, input: Any?) = error("unused")
    }


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
