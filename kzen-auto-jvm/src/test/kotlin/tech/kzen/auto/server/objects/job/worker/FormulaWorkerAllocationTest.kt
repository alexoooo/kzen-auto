package tech.kzen.auto.server.objects.job.worker

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelInputIterator
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.plugin.model.record.FlatRecordHeader
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.value.DataNode
import tech.kzen.lib.common.exec.data.value.DataOverlay
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.LiteralDataValues
import tech.kzen.lib.common.exec.data.value.ValueMetadata
import tech.kzen.lib.common.model.location.ObjectLocation
import java.lang.management.ManagementFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue


/**
 * A Formula row gets metadata of its own (the incoming metadata with its calculated field set) without a contract
 * of its own: measured as this thread's allocated bytes per row in steady state, over rows sharing one file's
 * metadata as Parse emits them. The number is printed for the session record.
 */
class FormulaWorkerAllocationTest {
    private val batchRows = 1_000
    private val warmupBatches = 20
    private val measuredBatches = 50

    // The row's evaluation, its calculated literal and its metadata record; composing and validating the metadata
    // contract per row costs several KB more
    private val budgetBytesPerRow = 2_000L

    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    private val text = DataType.Scalar(ScalarKind.Text)
    private val payload = DataContract(DataType.Record(listOf(
        DataField(FieldId("group"), text),
        DataField(FieldId("amount"), text))))
    private val file = ValueMetadata.of(DataOverlay.record(null, listOf(
        FieldId("name") to LiteralDataValues.lift("input.csv", DataContract(text)))))
    private val parsed = ValueMetadata.of(DataOverlay.record(null, listOf(FieldId("parent") to file.value)))
    private val contract = payload.withMetadata(parsed.contract)

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
    fun aRowWithFreshMetadataAllocatesNoContract() = runBlocking {
        if (!threads.isThreadAllocatedMemoryEnabled) {
            threads.isThreadAllocatedMemoryEnabled = true
        }
        val rows = List(batchRows) { index ->
            val record = FlatFileRecord.of(listOf("g", index.toString()))
            record.attachHeader(FlatRecordHeader(payload))
            DataValue(record, DataNode(0), parsed)
        }
        val input = MeasuredInput(rows)
        val output = CheckingOutput()
        val worker = FormulaWorker(
            input,
            output,
            FormulaSpec(mapOf("width" to "amount.length")),
            "",
            ObjectLocation.parse("test/formula-allocation-test.yaml#main.workers/formula"),
            context.jobExpressionCompiler)

        worker.run(DeclaredInputControl(contract))

        val measuredRows = measuredBatches.toLong() * batchRows
        val perRow = input.allocated / measuredRows
        println("WR11 Formula allocation with fresh metadata: $perRow bytes per row")
        assertEquals((warmupBatches + measuredBatches).toLong() * batchRows, output.count)
        assertTrue(perRow < budgetBytesPerRow, "$perRow bytes per row")
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Measures this thread's allocation from the first measured batch's delivery to the end of the input
    private inner class MeasuredInput(private val rows: List<DataValue>): ChannelInput<Any?> {
        private var delivered = 0
        private var before = 0L
        var allocated = 0L

        override suspend fun receiveBatch(): List<Any?>? {
            if (delivered == warmupBatches) {
                before = threads.currentThreadAllocatedBytes
            }
            if (delivered == warmupBatches + measuredBatches) {
                allocated = threads.currentThreadAllocatedBytes - before
                return null
            }
            delivered += 1
            return rows
        }

        override suspend fun receive(): Any? = error("unused")
        override fun iterator(): ChannelInputIterator<Any?> = error("unused")
    }


    // Reads each output's calculated field, as a downstream Format or Write does
    private class CheckingOutput: ChannelOutput<DataValue> {
        var count = 0L

        override suspend fun send(element: DataValue) {
            val metadata = checkNotNull(element.metadata)
            val width = metadata.access.readLong(metadata.access.field(metadata.root, FieldId("width")))
            check(width > 0)
            count += 1
        }

        override suspend fun flush() {}
        override fun batchSize(): Int = 1_000
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
}
