package tech.kzen.auto.common.objects.document.job.preview

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PreviewTableTest {
    @Test
    fun wideMixedRecordsKeepFullItemsAccessibleWithinTheColumnLimit() {
        val items = (0..300).map { index ->
            PreviewNode("record", "1 field", listOf(PreviewNode("scalar", "$index", name = "field$index")))
        }
        val table = PreviewTable(items)
        assertEquals(200, table.columns.size)
        assertEquals("Item details", table.columns.last().label)
        assertEquals(items.last(), table.rows.last().last())
        assertEquals("absent", table.rows.last().first().kind)
    }

    @Test
    fun emptyRecordsRemainVisibleAndDistinctFromAbsentFields() {
        val empty = PreviewNode("record", "0 fields")
        val table = PreviewTable(listOf(empty))
        assertEquals(listOf("value"), table.columns.map { it.label })
        assertEquals(empty, table.rows.single().single())
    }

    @Test
    fun fieldOccurrencesAndRootValuesHaveDifferentIdentities() {
        val record = PreviewNode("record", "2 fields", listOf(
            PreviewNode("scalar", "first", name = "value"),
            PreviewNode("null", "null", name = "value", occurrence = 1)))
        val table = PreviewTable(listOf(record, PreviewNode("scalar", "root")))
        assertEquals(3, table.columns.distinct().size)
        assertTrue(table.columns.last().root)
        assertEquals("null", table.rows.first()[1].kind)
        assertEquals("absent", table.rows.last()[1].kind)
    }

    @Test
    fun metadataFieldsFollowThePayloadColumnsWithAMetaPrefix() {
        val metadata = PreviewNode("record", "1 field", listOf(PreviewNode("scalar", "a.csv", name = "name")))
        val row = PreviewNode("record", "1 field", listOf(PreviewNode("scalar", "1", name = "name")), metadata = metadata)
        val table = PreviewTable(listOf(row, PreviewNode("scalar", "root")))
        assertEquals(listOf("name", "value", "meta.name"), table.columns.map { it.label })
        assertEquals(listOf("1", "Absent", "a.csv"), table.rows.first().map { it.text })
        assertEquals("absent", table.rows.last().last().kind)
    }
}
