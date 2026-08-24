package tech.kzen.auto.common.data.file

import tech.kzen.auto.common.objects.document.plugin.model.CommonDataEncodingSpec
import tech.kzen.auto.common.objects.document.plugin.model.CommonPluginCoordinate
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.service.parse.YamlNotationParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull


class FileSelectionSpecTest {
    private fun parse(yaml: String): FileSelectionSpec {
        val notation = YamlNotationParser().parseAttribute(yaml) as ListAttributeNotation
        return FileSelectionSpec.ofNotation(notation)
    }


    @Test
    fun notationRoundTrip() {
        val original = FileSelectionSpec(listOf(
            FileSelectionEntry(
                DataLocation.of("input/data.csv"),
                CommonPluginCoordinate.ofString("Csv"),
                CommonDataEncodingSpec.ofString("UTF-8")),
            FileSelectionEntry(DataLocation.of("input/raw.tsv"), null, null)))

        assertEquals(original, FileSelectionSpec.ofNotation(original.asNotation()))
    }


    @Test
    fun blankOptionalValuesNormalizeToNullAndAreOmittedOnEmission() {
        val entry = parse("- location: input/data.csv\n  format: ''\n  encoding: ''\n").entries.single()

        assertNull(entry.format)
        assertNull(entry.encoding)
        val emitted = entry.asNotation().map.keys.map { it.asKey() }
        assertEquals(listOf(FileSelectionEntry.locationKey), emitted)
        assertFalse(FileSelectionEntry.formatKey in emitted)
        assertFalse(FileSelectionEntry.encodingKey in emitted)
    }


    @Test
    fun editorShapePreservesEntryOrderAndOmitsBlankOverrides() {
        val editorValue = FileSelectionSpec(listOf(
            FileSelectionEntry(DataLocation.of("input/z.csv"), null, null),
            FileSelectionEntry(
                DataLocation.of("input/a.csv"),
                CommonPluginCoordinate.ofString("Csv"),
                CommonDataEncodingSpec.ofString("UTF-8"))))

        val expected = parse("""
            - location: input/z.csv
            - location: input/a.csv
              format: Csv
              encoding: UTF-8
        """.trimIndent())
        assertEquals(expected.asNotation(), editorValue.asNotation())
    }
}
