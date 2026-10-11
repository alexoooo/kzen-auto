package tech.kzen.auto.common.objects.document.job.path

import tech.kzen.auto.common.objects.document.job.path.WriterColumnSpec.WriterColumn
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.InsertListItemInAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.UpdateInAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.platform.collect.persistentListOf
import tech.kzen.lib.platform.collect.persistentMapOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue


/** A writer's column list read from notation, and the editor's commands over it. */
class WriterColumnSpecTest {
    private val writer = ObjectLocation.parse("job.yaml#main.workers/write")
    private val columns = AttributeName("columns")
    private val name = ProjectionPath.parse("meta.name")


    @Test
    fun readsPayloadFieldsAndPathColumnsInOrder() {
        val spec = WriterColumnSpec.ofNotation(ListAttributeNotation(persistentListOf(
            ScalarAttributeNotation("*"),
            ScalarAttributeNotation("meta.name"))))
        assertEquals(listOf(WriterColumn.PayloadFields.every, WriterColumn.Path(PathProjectionEntry(name))), spec.columns)
        assertEquals(listOf(name), spec.pathEntries().map { it.path })
    }


    @Test
    fun aStarMapNarrowsAndRenamesThePayloadColumns() {
        val star = MapAttributeNotation(persistentMapOf(
            AttributeSegment.ofKey("path") to ScalarAttributeNotation("*"),
            AttributeSegment.ofKey("exclude") to ListAttributeNotation(persistentListOf(ScalarAttributeNotation("c0"))),
            AttributeSegment.ofKey("as") to MapAttributeNotation(persistentMapOf(
                AttributeSegment.ofKey("c1") to ScalarAttributeNotation("value")))))
        val spec = WriterColumnSpec.ofNotation(ListAttributeNotation(persistentListOf(star)))

        val column = assertIs<WriterColumn.PayloadFields>(spec.columns.single())
        assertEquals(ColumnPatterns(listOf(), listOf("c0")), column.patterns)
        assertEquals("value", column.outputName("c1"))
        assertFalse(spec.isPayloadFieldsOnly())
        assertTrue(spec.readsPayload())
        assertEquals(star, WriterColumnSpec.payloadFieldsNotation(column))
    }


    @Test
    fun aStarThatNeitherNarrowsNorRenamesIsWrittenBare() {
        val narrowed = WriterColumn.PayloadFields(ColumnPatterns(listOf(), listOf("c0")))
        val widened = narrowed.withPatterns(ColumnPatterns.none)
        assertEquals(ScalarAttributeNotation("*"), WriterColumnSpec.payloadFieldsNotation(widened))
        assertEquals(widened, narrowed.withRename("c1", "x").withRename("c1", " ").withPatterns(ColumnPatterns.none))
    }


    @Test
    fun theFirstEditOfTheImplicitStarWritesTheList() {
        val column = WriterColumn.PayloadFields.every.withRename("c1", "value")
        val upsert = assertIs<UpsertAttributeCommand>(
            WriterColumnSpec.payloadFieldsCommand(writer, columns, -1, column))
        assertEquals(
            listOf(column),
            WriterColumnSpec.ofNotation(upsert.attributeNotation as ListAttributeNotation).columns)

        val update = assertIs<UpdateInAttributeCommand>(
            WriterColumnSpec.payloadFieldsCommand(writer, columns, 0, column))
        assertEquals(AttributePath.parse("columns.0"), update.attributePath)
    }


    @Test
    fun theFirstPathAddedKeepsThePayloadColumns() {
        val command = WriterColumnSpec.addPathCommand(writer, columns, WriterColumnSpec.payloadFields, name)
        val upsert = assertIs<UpsertAttributeCommand>(command)
        assertEquals(columns, upsert.attributeName)
        val written = WriterColumnSpec.ofNotation(upsert.attributeNotation as ListAttributeNotation)
        assertEquals(listOf(WriterColumn.PayloadFields.every, WriterColumn.Path(PathProjectionEntry(name))), written.columns)
    }


    @Test
    fun laterPathsAppendToTheWritersOwnList() {
        val current = WriterColumnSpec(listOf(WriterColumn.PayloadFields.every))
        val command = WriterColumnSpec.addPathCommand(writer, columns, current, name)
        val insert = assertIs<InsertListItemInAttributeCommand>(command)
        assertEquals(AttributePath.ofName(columns), insert.containingList)
        assertEquals(ScalarAttributeNotation("meta.name"), insert.item)
    }
}
