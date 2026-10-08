package tech.kzen.auto.server.objects.job.worker.format

import org.junit.Test
import tech.kzen.auto.common.objects.document.job.path.WriterColumnSpec
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.plugin.model.record.FlatRecordHeader
import tech.kzen.auto.server.data.write.delimited.ConfiguredDelimitedWriterCapability
import tech.kzen.auto.server.objects.datasource.format.ConfiguredDelimitedTestFormats
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.objects.job.value.recycle.Recyclable
import tech.kzen.auto.server.objects.job.value.recycle.RecyclablePool
import tech.kzen.auto.server.objects.job.worker.WriterColumns
import tech.kzen.auto.server.objects.job.worker.content.Bytes
import tech.kzen.auto.server.objects.job.worker.content.PooledBytes
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.MetadataContract
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.value.DataNode
import tech.kzen.lib.common.exec.data.value.DataOverlay
import tech.kzen.lib.common.exec.data.value.DataState
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.LiteralDataValues
import tech.kzen.lib.common.exec.data.value.ValueMetadata
import tech.kzen.lib.common.util.digest.Digest
import java.io.ByteArrayOutputStream
import java.lang.management.ManagementFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue


/**
 * Format's steady state allocates nothing per record: a flat record (Parse's rows, including a superset's absent
 * field) is read in place, its name resolved into a reused builder (a column and a metadata field, repeating), and
 * encoded into a recycled chunk that keeps its value. Measured as this thread's allocated bytes, a chunk's cycle
 * through a channel (hold, release) included; the number is printed for the session record.
 */
class FormatAllocationTest {
    private val warmupRecords = 50_000
    private val measuredRecords = 200_000

    private val freshMetadataCount = 1024
    private val freshMetadataWarmup = 20_000
    private val freshMetadataRecords = 50_000
    // The chunk's metadata object, most of it the validation of its contract a ValueMetadata makes (about 3.7 KB);
    // building the contract itself per record costs about 14 KB
    private val freshMetadataBudgetBytes = 6_000L

    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    private val text = DataType.Scalar(ScalarKind.Text)
    private val payload = DataContract(DataType.Record(listOf(
        DataField(FieldId("group"), text),
        DataField(FieldId("value"), text),
        DataField(FieldId("extra"), text))))
    private val metadata = ValueMetadata.of(DataOverlay.record(null, listOf(
        FieldId("file") to LiteralDataValues.lift("a", DataContract(text)))))
    private val contract = payload.withMetadata(MetadataContract.of(DataOverlay.contract(null, listOf(
        FieldId("file") to DataContract(text)))))


    @Test
    fun aRecordInSteadyStateAllocatesNothing() {
        if (!threads.isThreadAllocatedMemoryEnabled) {
            threads.isThreadAllocatedMemoryEnabled = true
        }
        val records = listOf(
            flat("x", "1", "first"),
            flat("x", "say \"hi\"", "a,b"),
            projected("x", "3"))
        val chunker = chunker("\${file}-\${group}\${extension}")

        val first = chunker.chunk(records[0], 1)
        assertEquals("group,value,extra\nx,1,first\n", copy(first))
        recycle(first)
        for (i in 1 until warmupRecords) {
            recycle(chunker.chunk(records[i % records.size], i + 1L))
        }

        val before = threads.currentThreadAllocatedBytes
        for (i in 0 until measuredRecords) {
            recycle(chunker.chunk(records[i % records.size], i + 1L))
        }
        val allocated = threads.currentThreadAllocatedBytes - before

        println("WR3 Format allocation: $allocated bytes over $measuredRecords records")
        // Anything per record would cost at least one object header (16 bytes) per record
        val budgetBytes = 64 * 1024
        assertTrue(allocated < budgetBytes, "$allocated bytes allocated over $measuredRecords records")
    }


    /**
     * A record whose metadata object is new each time (a Formula's calculated field, set per row) needs chunk
     * metadata of its own, but not a new contract while the parent's contract stays equal.
     */
    @Test
    fun freshParentMetadataReusesTheChunkContract() {
        if (!threads.isThreadAllocatedMemoryEnabled) {
            threads.isThreadAllocatedMemoryEnabled = true
        }
        val record = FlatFileRecord.of(listOf("x", "1", "first"))
        record.attachHeader(FlatRecordHeader(payload))
        val parents = List(freshMetadataCount) { index ->
            ValueMetadata.of(DataOverlay.record(metadata.value, listOf(
                FieldId("total") to LiteralDataValues.lift(index.toString(), DataContract(text)))))
        }
        val records = parents.map { DataValue(record, DataNode(0), it) }
        val chunker = chunker("\${file}\${extension}")

        for (i in 0 until freshMetadataWarmup) {
            recycle(chunker.chunk(records[i % records.size], i + 1L))
        }

        val before = threads.currentThreadAllocatedBytes
        for (i in 0 until freshMetadataRecords) {
            recycle(chunker.chunk(records[i % records.size], i + 1L))
        }
        val perRecord = (threads.currentThreadAllocatedBytes - before) / freshMetadataRecords

        println("WR9 Format allocation with fresh parent metadata: $perRecord bytes per record")
        assertTrue(perRecord < freshMetadataBudgetBytes, "$perRecord bytes per record")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun chunker(template: String): FormatChunker {
        val format = ConfiguredDelimitedTestFormats.csv()
        val columns = WriterColumns(WriterColumnSpec.payloadFields)
        val cells = FormatCells(columns, contract)
        val encoder = ConfiguredDelimitedWriterCapability.encoder(format, columns.header(contract) { it.render() })
        val name = FormatName(template, ".csv", cells, contract)
        return FormatChunker(encoder, cells, name, RecyclablePool { PooledBytes(it) }, Digest.empty)
    }


    private fun flat(vararg fields: String): DataValue {
        val record = FlatFileRecord.of(fields.toList())
        record.attachHeader(FlatRecordHeader(payload))
        return DataValue(record, DataNode(0), metadata)
    }


    private fun projected(vararg fields: String): DataValue {
        val record = FlatFileRecord.of(fields.toList() + "")
        val states = fields.map { DataState.Present } + DataState.Absent
        return JobDataValues.projectedRecord(payload, record, states).withMetadata(metadata)
    }


    private fun copy(chunk: DataValue): String {
        val output = ByteArrayOutputStream()
        (JobDataValues.native(chunk) as Bytes).writeTo(output)
        return output.toString(Charsets.UTF_8)
    }


    // What a channel does with a sent chunk: holds it, then releases it once its consumer's callback returns
    private fun recycle(chunk: DataValue) {
        val slot = Recyclable.of(chunk)!!
        slot.hold()
        slot.release()
    }
}
