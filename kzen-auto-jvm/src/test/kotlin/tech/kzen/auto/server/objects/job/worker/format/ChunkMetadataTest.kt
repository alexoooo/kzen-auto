package tech.kzen.auto.server.objects.job.worker.format

import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.objects.job.worker.content.FileValues
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.value.DataAccessException
import tech.kzen.lib.common.exec.data.value.DataOverlay
import tech.kzen.lib.common.exec.data.value.DataSnapshot
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.LiteralDataValues
import tech.kzen.lib.common.exec.data.value.SnapshotResult
import tech.kzen.lib.common.exec.data.value.ValueMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame


/**
 * Chunk metadata reads as the overlay `{group, format, parent}` it stands for: contract, fields, snapshot and
 * failures.
 */
class ChunkMetadataTest {
    private val text = DataContract(DataType.Scalar(ScalarKind.Text))

    private val file = ValueMetadata.of(DataOverlay.record(null, listOf(
        FieldId(FileValues.name) to LiteralDataValues.lift("input.csv", text))))

    private val format = ChunkMetadata.format("csv")

    private fun row(total: String) = ValueMetadata.of(DataOverlay.record(file.value, listOf(
        FieldId("total") to LiteralDataValues.lift(total, text),
        FieldId(FileValues.parent) to file.value)))


    @Test
    fun readsAsTheOverlayItStandsFor() {
        val parent = row("12.5")
        val chunk = metadata("x", parent)
        val overlay = overlay("x", parent)

        assertEquals(overlay.contract, chunk.contract)
        assertEquals(snapshot(overlay.value), snapshot(chunk.value))
        for (path in listOf(
            listOf(ChunkMetadata.group),
            listOf(ChunkMetadata.format, ChunkMetadata.extension),
            listOf(FileValues.parent, "total"),
            listOf(FileValues.parent, FileValues.name),
            listOf(FileValues.parent, FileValues.parent, FileValues.name))
        ) {
            assertEquals(
                JobDataValues.metadataText(overlay.value, *path.toTypedArray()),
                JobDataValues.metadataText(chunk.value, *path.toTypedArray()),
                path.joinToString("."))
        }
        assertEquals("x", JobDataValues.metadataText(chunk.value, ChunkMetadata.group))
        assertEquals("csv", JobDataValues.metadataText(chunk.value, ChunkMetadata.format, ChunkMetadata.extension))
        assertEquals("12.5", JobDataValues.metadataText(chunk.value, FileValues.parent, "total"))
        assertEquals(3, chunk.access.size(chunk.root))
    }


    @Test
    fun withoutParentOnlyTheGroupAndFormatAreFields() {
        val chunk = metadata("", null)

        assertEquals(overlay("", null).contract, chunk.contract)
        assertEquals(2, chunk.access.size(chunk.root))
        assertFailsWith<DataAccessException> { chunk.access.field(chunk.root, FieldId(FileValues.parent)) }
    }


    @Test
    fun theRootIsAComposedRecord() {
        val chunk = metadata("x", row("1"))

        assertFailsWith<DataAccessException> { chunk.access.native(chunk.root) }
        assertFailsWith<DataAccessException> { chunk.access.readText(chunk.root) }
        assertFailsWith<DataAccessException> { chunk.access.field(chunk.root, FieldId("other")) }
    }


    @Test
    fun aShapeServesEveryParentOfItsContract() {
        val shape = ChunkMetadata.shape(row("1").value.payloadContract)
        val group = ChunkMetadata.group("x")

        val first = shape.metadata(null, ChunkMetadata.values(group, format, row("1")))
        val second = shape.metadata(null, ChunkMetadata.values(group, format, row("2")))

        assertSame(first.access.contract(first.root), second.access.contract(second.root))
        assertEquals("1", JobDataValues.metadataText(first.value, FileValues.parent, "total"))
        assertEquals("2", JobDataValues.metadataText(second.value, FileValues.parent, "total"))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun metadata(group: String, parent: ValueMetadata?): ValueMetadata =
        ChunkMetadata.shape(parent?.value?.payloadContract)
            .metadata(null, ChunkMetadata.values(ChunkMetadata.group(group), format, parent))


    private fun overlay(group: String, parent: ValueMetadata?): ValueMetadata {
        val fields = ArrayList<Pair<FieldId, DataValue>>()
        fields += FieldId(ChunkMetadata.group) to LiteralDataValues.lift(group, text)
        fields += FieldId(ChunkMetadata.format) to DataOverlay.record(null, listOf(
            FieldId(ChunkMetadata.extension) to LiteralDataValues.lift("csv", text)))
        if (parent != null) {
            fields += FieldId(FileValues.parent) to parent.value
        }
        return ValueMetadata.of(DataOverlay.record(null, fields))
    }


    private fun snapshot(value: DataValue): DataSnapshot =
        (DataSnapshot.capture(value) as SnapshotResult.Complete).snapshot
}
