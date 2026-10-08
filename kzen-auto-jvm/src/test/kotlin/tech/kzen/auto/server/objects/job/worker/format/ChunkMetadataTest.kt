package tech.kzen.auto.server.objects.job.worker.format

import tech.kzen.auto.server.objects.job.worker.content.FileNameTemplate
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


/** Chunk metadata reads as the overlay `{name, parent}` it stands for: contract, fields, snapshot and failures. */
class ChunkMetadataTest {
    private val text = DataContract(DataType.Scalar(ScalarKind.Text))

    private val file = ValueMetadata.of(DataOverlay.record(null, listOf(
        FieldId(FileValues.name) to LiteralDataValues.lift("input.csv", text))))

    private fun row(total: String) = ValueMetadata.of(DataOverlay.record(file.value, listOf(
        FieldId("total") to LiteralDataValues.lift(total, text),
        FieldId(FileValues.parent) to file.value)))


    @Test
    fun readsAsTheOverlayItStandsFor() {
        val parent = row("12.5")
        val chunk = metadata("out.csv", parent)
        val overlay = overlay("out.csv", parent)

        assertEquals(overlay.contract, chunk.contract)
        assertEquals(snapshot(overlay.value), snapshot(chunk.value))
        for (path in listOf("name", "parent.total", "parent.name", "parent.parent.name")) {
            assertEquals(
                FileNameTemplate.metadataText(overlay.value, path),
                FileNameTemplate.metadataText(chunk.value, path),
                path)
        }
        assertEquals("12.5", FileNameTemplate.metadataText(chunk.value, "parent.total"))
        assertEquals(2, chunk.access.size(chunk.root))
    }


    @Test
    fun withoutParentOnlyTheNameIsAField() {
        val chunk = metadata("out.csv", null)

        assertEquals(overlay("out.csv", null).contract, chunk.contract)
        assertEquals(1, chunk.access.size(chunk.root))
        assertFailsWith<DataAccessException> { chunk.access.field(chunk.root, FieldId(FileValues.parent)) }
    }


    @Test
    fun theRootIsAComposedRecord() {
        val chunk = metadata("out.csv", row("1"))

        assertFailsWith<DataAccessException> { chunk.access.native(chunk.root) }
        assertFailsWith<DataAccessException> { chunk.access.readText(chunk.root) }
        assertFailsWith<DataAccessException> { chunk.access.field(chunk.root, FieldId("other")) }
    }


    @Test
    fun aShapeServesEveryParentOfItsContract() {
        val shape = ChunkMetadata.shape(row("1").value.payloadContract)
        val name = ChunkMetadata.name("out.csv")

        val first = shape.metadata(null, ChunkMetadata.values(name, row("1")))
        val second = shape.metadata(null, ChunkMetadata.values(name, row("2")))

        assertSame(first.access.contract(first.root), second.access.contract(second.root))
        assertEquals("1", FileNameTemplate.metadataText(first.value, "parent.total"))
        assertEquals("2", FileNameTemplate.metadataText(second.value, "parent.total"))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun metadata(name: String, parent: ValueMetadata?): ValueMetadata =
        ChunkMetadata.shape(parent?.value?.payloadContract)
            .metadata(null, ChunkMetadata.values(ChunkMetadata.name(name), parent))


    private fun overlay(name: String, parent: ValueMetadata?): ValueMetadata {
        val fields = ArrayList<Pair<FieldId, DataValue>>()
        fields += FieldId(FileValues.name) to LiteralDataValues.lift(name, text)
        if (parent != null) {
            fields += FieldId(FileValues.parent) to parent.value
        }
        return ValueMetadata.of(DataOverlay.record(null, fields))
    }


    private fun snapshot(value: DataValue): DataSnapshot =
        (DataSnapshot.capture(value) as SnapshotResult.Complete).snapshot
}
