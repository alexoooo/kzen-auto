package tech.kzen.auto.server.objects.job.worker.preview

import org.junit.Test
import tech.kzen.auto.common.objects.document.job.preview.PreviewNode
import tech.kzen.auto.common.objects.document.job.preview.PreviewTable
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.lib.common.exec.*
import tech.kzen.lib.common.exec.data.type.*
import tech.kzen.lib.common.exec.data.value.*
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import java.util.concurrent.CancellationException
import kotlin.test.*

class PreviewCaptureTest {
    private val capture = PreviewCapture(maximumNanos = Long.MAX_VALUE)

    @Test
    fun nestedNativeRecordIsDetachedAndReadableAfterClose() {
        val source = Day("AAPL", Detail(true))
        val snapshot = capture.capture(JobDataValues.lift(source))
        source.close()
        assertEquals(1, source.closes)
        assertEquals("AAPL", snapshot.children.single { it.name == "symbol" }.text)
        assertEquals("true", snapshot.children.single { it.name == "day" }.children.single().text)
        assertEquals(snapshot, PreviewNode.decode(snapshot.encode()))
        assertFalse(snapshot.partial)
    }

    @Test
    fun listsMapsExactNumbersNullAndBinaryAreCopied() {
        val bytes = byteArrayOf(1, 2, 3)
        val value = LiteralDataValues.lift(recordOf(
            "list" to listOf(Long.MAX_VALUE), "map" to mapOf(2 to "two"), "null" to null,
            "bytes" to bytes))
        val snapshot = capture.capture(value)
        bytes[0] = 9
        assertEquals(Long.MAX_VALUE.toString(), snapshot.children.single { it.name == "list" }.children.single().text)
        assertEquals("two", snapshot.children.single { it.name == "map" }.children.single().children[1].text)
        assertEquals("null", snapshot.children.single { it.name == "null" }.kind)
        assertTrue(snapshot.children.single { it.name == "bytes" }.text.startsWith("01 02 03"))
    }

    @Test
    fun unreadableAndOpaqueBranchesDoNotHideReadableSiblings() {
        val original = LiteralDataValues.lift(recordOf("bad" to "x", "good" to "y", "opaque" to Any()))
        val access = object: ValueAccess by original.access {
            override fun field(node: DataNode, field: FieldId): DataNode {
                if (field.name == "bad") error("unreadable field")
                return original.access.field(node, field)
            }
        }
        val snapshot = capture.capture(DataValue(access, original.root))
        assertTrue(snapshot.partial)
        assertEquals("unavailable", snapshot.children[0].kind)
        assertEquals("y", snapshot.children[1].text)
        assertEquals("unavailable", snapshot.children[2].kind)
    }

    @Test
    fun boundedCapturePreservesPrefixAndMarksOmissions() {
        val listing = LiteralDataValues.lift(listOf("abcdef", "second", "third"))
        val snapshot = PreviewCapture(maximumChildren = 2, maximumText = 3, maximumNanos = Long.MAX_VALUE).capture(listing)
        assertEquals("abc…", snapshot.children[0].text)
        assertEquals("truncated", snapshot.children.last().kind)
        assertTrue(snapshot.partial)
        val huge = capture.capture(LiteralDataValues.lift(List(200) { "x".repeat(4096) }))
        assertTrue(huge.partial)
        assertTrue(huge.encode().toByteArray().size <= 256 * 1024)
        val nodes = PreviewCapture(maximumNodes = 2, maximumNanos = Long.MAX_VALUE).capture(listing)
        assertEquals("truncated", nodes.children.last().kind)
        val deep = PreviewCapture(maximumDepth = 1, maximumNanos = Long.MAX_VALUE)
            .capture(LiteralDataValues.lift(listOf(listOf(listOf(1)))))
        assertEquals("truncated", deep.children.single().children.single().kind)
        val timed = PreviewCapture(maximumNanos = -1).capture(listing)
        assertEquals("truncated", timed.kind)
    }

    @Test
    fun recursiveContractsDetectCyclesWithoutCallingNativeToString() {
        val id = DefinitionId("node")
        val type = DataType.Record(listOf(DataField(FieldId("next"), DataType.Reference(id))))
        val contract = DataContract(type, mapOf(DataTypePath.root to TypeMetadata.any), mapOf(id to type),
            mapOf(id to mapOf(DataTypePath.root to TypeMetadata.any)))
        val identity = object { override fun toString(): String = error("never render native") }
        val access = object: ValueAccess by LiteralDataValues.lift(0).access {
            override fun contract(node: DataNode) = contract
            override fun state(node: DataNode) = DataState.Present
            override fun field(node: DataNode, field: FieldId) = node
            override fun native(node: DataNode) = identity
        }
        assertEquals("cycle", capture.capture(DataValue(access, DataNode(0))).children.single().kind)
    }

    @Test
    fun duplicateFieldsAbsentFieldsAndMixedShapesKeepColumnIdentity() {
        val type = DataType.Record(listOf(
            DataField(FieldId("value"), DataType.Scalar(ScalarKind.Text)),
            DataField(FieldId("value", 1), DataType.Scalar(ScalarKind.Text), optional = true)))
        val access = object: ValueAccess by LiteralDataValues.lift("first").access {
            override fun contract(node: DataNode) = DataContract(type)
            override fun state(node: DataNode) = if (node.token == 2L) DataState.Absent else DataState.Present
            override fun field(node: DataNode, field: FieldId) = DataNode(field.occurrence.toLong() + 1)
            override fun scalar(node: DataNode) = TextExecutionValue("first")
        }
        val item = capture.capture(DataValue(access, DataNode(0)))
        val table = PreviewTable(listOf(item, PreviewNode("scalar", "root")))
        assertEquals(3, table.columns.size)
        assertEquals("absent", table.rows[0][1].kind)
        assertEquals("root", table.rows[1][2].text)
    }

    @Test
    fun unionAndDynamicUseRuntimeStructureAndCancellationPropagates() {
        val variant = VariantId("text")
        val union = DataContract(DataType.Union(listOf(DataVariant(variant, DataType.Scalar(ScalarKind.Text)))))
        val backing = LiteralDataValues.lift("value")
        val access = object: ValueAccess by backing.access {
            override fun contract(node: DataNode) = if (node.token == 0L) union else backing.contract
            override fun state(node: DataNode) = DataState.Present
            override fun activeVariant(node: DataNode) = variant
            override fun selected(node: DataNode) = DataNode(1)
            override fun scalar(node: DataNode) = TextExecutionValue("value")
        }
        assertEquals("value", capture.capture(DataValue(access, DataNode(0))).children.single().text)
        val record = LiteralDataValues.lift(recordOf("dynamic" to "resolved"), DataContract(DataType.Record(listOf(
            DataField(FieldId("dynamic"), DataType.Dynamic())))))
        assertEquals("resolved", capture.capture(record).children.single().text)
        val cancelled = object: ValueAccess by backing.access {
            override fun state(node: DataNode): DataState = throw CancellationException()
        }
        assertFailsWith<CancellationException> { capture.capture(DataValue(cancelled, backing.root)) }
    }

    class Detail(var open: Boolean)
    class Day(val symbol: String, val day: Detail): AutoCloseable {
        var closes = 0
        override fun close() { closes++; day.open = false }
    }
}
