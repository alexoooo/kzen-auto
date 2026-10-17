package tech.kzen.auto.server.objects.job.worker.format

import tech.kzen.auto.common.objects.document.job.path.WriterColumnSpec
import tech.kzen.auto.plugin.model.data.DataRecordBuffer
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.plugin.model.record.FlatRecordHeader
import tech.kzen.auto.server.data.write.RecordCells
import tech.kzen.auto.server.data.write.RecordEncoder
import tech.kzen.auto.server.data.write.RecordOutputState
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.objects.job.value.recycle.RecyclablePool
import tech.kzen.auto.server.objects.job.worker.WriterColumns
import tech.kzen.auto.server.objects.job.worker.content.Bytes
import tech.kzen.auto.server.objects.job.worker.content.FileValues
import tech.kzen.auto.server.objects.job.worker.content.PooledBytes
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
import tech.kzen.lib.common.util.digest.Digest
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import tech.kzen.lib.common.exec.engine.resource.Holder


/**
 * [FormatChunker] over a format that remembers something per output and ends each with a footer: records of
 * interleaved groups go to their own outputs, each framed once, with one footer after its last record; the records of
 * a group between flushes are one chunk.
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
        val ends: Boolean,
        val file: String? = null
    )


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun interleavedGroupsAreEachOneFramedOutput() {
        val chunker = chunker(endsOnChange = false)

        val seen = chunk(chunker, "a" to "1", "b" to "2", "a" to "3", "b" to "4", "a" to "5")
            .plus(complete(chunker))

        assertEquals(
            listOf(
                Seen("a", "[1,3,5", 1, false),
                Seen("b", "[2,4", 1, false),
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
                Seen("a", "[1,2", 1, false),
                Seen("a", "]", 0, true),
                Seen("b", "[3", 1, false),
                Seen("b", "]", 0, true)),
            seen)
    }


    @Test
    fun eachFlushSendsTheRecordsSinceTheLastOneAsOneChunkHeadedByItsFirstRecordsMetadata() {
        val chunker = chunker(endsOnChange = false)
        val first = metadata("first")

        val seen = chunk(chunker, "a" to "1", "a" to "2", metadata = listOf(first, metadata("second")))
            .plus(chunk(chunker, "a" to "3"))
            .plus(complete(chunker))

        assertEquals(
            listOf(
                Seen("a", "[1,2", 1, false, "first"),
                Seen("a", ",3", 0, false, null),
                Seen("a", "]", 0, true, null)),
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

    /** Chunks [records] as one input batch, the i-th with the i-th of [metadata] if any, then flushes. */
    private fun chunk(
        chunker: FormatChunker,
        vararg records: Pair<String, String>,
        metadata: List<ValueMetadata?> = emptyList()
    ): List<Seen> {
        val out = ArrayList<DataValue>()
        for ((index, record) in records.withIndex()) {
            val flat = FlatFileRecord.of(listOf(record.first, record.second))
            flat.attachHeader(FlatRecordHeader(payload))
            ordinal += 1
            chunker.chunk(DataValue(flat, DataNode(0), metadata.getOrNull(index)), ordinal, out)
        }
        chunker.flush(out)
        return out.map(::seen)
    }


    private fun metadata(file: String): ValueMetadata =
        ValueMetadata.of(DataOverlay.record(null, listOf(
            FieldId("file") to LiteralDataValues.lift(file, DataContract(text)))))


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
            bytes.endsOutput(),
            JobDataValues.metadataText(chunk.metadata?.value, FileValues.parent, "file"))
        chunk.owners!!.hold(Holder("test")).release()
        return seen
    }
}
