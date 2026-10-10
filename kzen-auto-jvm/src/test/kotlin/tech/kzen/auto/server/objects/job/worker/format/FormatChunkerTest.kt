package tech.kzen.auto.server.objects.job.worker.format

import tech.kzen.auto.common.objects.document.job.path.WriterColumnSpec
import tech.kzen.auto.plugin.model.data.DataRecordBuffer
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.plugin.model.record.FlatRecordHeader
import tech.kzen.auto.server.data.write.RecordCells
import tech.kzen.auto.server.data.write.RecordEncoder
import tech.kzen.auto.server.data.write.RecordOutputState
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
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.value.DataNode
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.util.digest.Digest
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith


/**
 * [FormatChunker] over a format that remembers something per output and ends each with a footer: records of
 * interleaved groups go to their own outputs, each framed once, with one footer after its last record.
 */
class FormatChunkerTest {
    private val text = DataType.Scalar(ScalarKind.Text)
    private val payload = DataContract(DataType.Record(listOf(
        DataField(FieldId("group"), text),
        DataField(FieldId("value"), text))))


    /** A JSON-like array: `[` first, `,` between records, `]` last; what goes between depends on the output. */
    private class BracketEncoder: RecordEncoder {
        private class Output: RecordOutputState {
            var records = 0
        }

        override fun openOutput(): RecordOutputState =
            Output()

        override fun encodeHeader(state: RecordOutputState, output: DataRecordBuffer) {
            append(output, "[")
        }

        override fun encodeRecord(state: RecordOutputState, cells: RecordCells, output: DataRecordBuffer) {
            val opened = state as Output
            if (opened.records > 0) {
                append(output, ",")
            }
            opened.records += 1
            append(output, cells.text(1).toString())
        }

        override fun encodeFooter(state: RecordOutputState, output: DataRecordBuffer) {
            append(output, "]")
        }

        private fun append(output: DataRecordBuffer, text: String) {
            val bytes = text.encodeToByteArray()
            output.ensureByteCapacity(output.bytesLength + bytes.size)
            bytes.copyInto(output.bytes, output.bytesLength)
            output.bytesLength += bytes.size
        }
    }


    /** What a chunk says: its group, its bytes, and where it starts or ends an output. */
    private data class Seen(
        val group: String?,
        val text: String,
        val header: Int,
        val ends: Boolean
    )


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun interleavedGroupsAreEachOneFramedOutput() {
        val chunker = chunker(endsOnChange = false)

        val seen = chunk(chunker, "a" to "1", "b" to "2", "a" to "3", "b" to "4", "a" to "5")
            .plus(complete(chunker))

        assertEquals(
            listOf(
                Seen("a", "[1", 1, false),
                Seen("b", "[2", 1, false),
                Seen("a", ",3", 0, false),
                Seen("b", ",4", 0, false),
                Seen("a", ",5", 0, false),
                Seen("a", "]", 0, true),
                Seen("b", "]", 0, true)),
            seen)
        assertEquals("[1,3,5]", seen.filter { it.group == "a" }.joinToString("") { it.text })
        assertEquals(2L, chunker.groups.done)
    }


    @Test
    fun onChangeEachOutputEndsBeforeTheNextGroupsFirstRecord() {
        val chunker = chunker(endsOnChange = true)

        val seen = chunk(chunker, "a" to "1", "a" to "2", "b" to "3")
            .plus(complete(chunker))

        assertEquals(
            listOf(
                Seen("a", "[1", 1, false),
                Seen("a", ",2", 0, false),
                Seen("a", "]", 0, true),
                Seen("b", "[3", 1, false),
                Seen("b", "]", 0, true)),
            seen)
    }


    @Test
    fun onChangeAGroupThatComesBackFails() {
        val chunker = chunker(endsOnChange = true)
        chunk(chunker, "a" to "1", "b" to "2")

        val failure = assertFailsWith<IllegalArgumentException> { chunk(chunker, "a" to "3") }

        assertContains(failure.message!!, "Record 3: Group 'a' came back after 'b'")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun chunker(endsOnChange: Boolean): FormatChunker {
        val cells = FormatCells(WriterColumns(WriterColumnSpec.payloadFields), payload)
        val groupOf = { element: DataValue -> (element.access as FlatFileRecord).getString(0) }
        return FormatChunker(
            BracketEncoder(), cells, groupOf, endsOnChange, "json", RecyclablePool { PooledBytes(it) }, Digest.empty)
    }


    private var ordinal = 0L

    private fun chunk(chunker: FormatChunker, vararg records: Pair<String, String>): List<Seen> {
        val out = ArrayList<DataValue>()
        for ((group, value) in records) {
            val record = FlatFileRecord.of(listOf(group, value))
            record.attachHeader(FlatRecordHeader(payload))
            ordinal += 1
            chunker.chunk(DataValue(record, DataNode(0)), ordinal, out)
        }
        return out.map(::seen)
    }


    private fun complete(chunker: FormatChunker): List<Seen> {
        val out = ArrayList<DataValue>()
        chunker.complete(out)
        return out.map(::seen)
    }


    private fun seen(chunk: DataValue): Seen {
        val bytes = JobDataValues.native(chunk) as Bytes
        val copy = ByteArrayOutputStream()
        bytes.writeTo(copy)
        val seen = Seen(
            JobDataValues.metadataText(chunk.metadata?.value, ChunkMetadata.group),
            copy.toString(Charsets.UTF_8),
            bytes.headerLength(),
            bytes.endsOutput())
        val slot = Recyclable.of(chunk)!!
        slot.hold()
        slot.release()
        return seen
    }
}
