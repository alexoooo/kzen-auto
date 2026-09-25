package tech.kzen.auto.common.objects.document.job.path

import tech.kzen.auto.common.objects.document.job.path.WriterColumnSpec.WriterColumn
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.InsertListItemInAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.platform.collect.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs


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
        assertEquals(listOf(WriterColumn.PayloadFields, WriterColumn.Path(PathProjectionEntry(name))), spec.columns)
        assertEquals(listOf(name), spec.pathEntries().map { it.path })
    }


    @Test
    fun theFirstPathAddedKeepsThePayloadColumns() {
        val command = WriterColumnSpec.addPathCommand(writer, columns, WriterColumnSpec.payloadFields, name)
        val upsert = assertIs<UpsertAttributeCommand>(command)
        assertEquals(columns, upsert.attributeName)
        val written = WriterColumnSpec.ofNotation(upsert.attributeNotation as ListAttributeNotation)
        assertEquals(listOf(WriterColumn.PayloadFields, WriterColumn.Path(PathProjectionEntry(name))), written.columns)
    }


    @Test
    fun laterPathsAppendToTheWritersOwnList() {
        val current = WriterColumnSpec(listOf(WriterColumn.PayloadFields))
        val command = WriterColumnSpec.addPathCommand(writer, columns, current, name)
        val insert = assertIs<InsertListItemInAttributeCommand>(command)
        assertEquals(AttributePath.ofName(columns), insert.containingList)
        assertEquals(ScalarAttributeNotation("meta.name"), insert.item)
    }
}
