package tech.kzen.auto.client.objects.document.common.file

import tech.kzen.auto.common.data.format.FileFormatCatalog
import tech.kzen.auto.common.data.format.ConfiguredFormatDetail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull


class DataFormatOptionsTest {
    private fun detail(reference: String, label: String, vararg extensions: String) =
        ConfiguredFormatDetail(reference, label, extensions.toList())


    private val catalog = FileFormatCatalog(
        listOf(detail("configured.yaml#ConfiguredCsv", "CSV", "csv"),
            detail("configured.yaml#ConfiguredText", "Text", "txt")),
        listOf("UTF-8", "ISO-8859-1"))


    @Test
    fun configuredFormatsHaveNominalReferencesAndFriendlyLabels() {
        val options = DataFormatOptions.formats(catalog, "")

        assertEquals(
            listOf("configured.yaml#ConfiguredCsv", "configured.yaml#ConfiguredText"),
            options.map { it.value })
        assertEquals(listOf("CSV", "Text"), options.map { it.label })
    }


    // The extensions are what tell a reader which format their file is likely to want.
    @Test
    fun aFormatCarriesItsExtensionsAsTheOptionDetail() {
        val csv = DataFormatOptions.formats(catalog, "")
            .single { it.value == "configured.yaml#ConfiguredCsv" }

        assertEquals(".csv", csv.detail)
        assertNull(DataFormatOptions.encodings(catalog, "").single { it.value == "UTF-8" }.detail)
    }


    // A format from an uninstalled plugin, or a charset this JVM lacks: the field must still read back what the
    // notation says, or the configuration looks unset and one stray click silently rewrites it.
    @Test
    fun aValueTheServerNoLongerOffersIsStillListed() {
        val formats = DataFormatOptions.formats(catalog, "Parquet")
        val encodings = DataFormatOptions.encodings(catalog, "EBCDIC")

        assertNotNull(formats.singleOrNull { it.value == "Parquet" }?.detail)
        assertNotNull(encodings.singleOrNull { it.value == "EBCDIC" }?.detail)
    }


    @Test
    fun aKnownValueIsNotDuplicatedAsAnUnknownOne() {
        assertEquals(1, DataFormatOptions.formats(catalog, "configured.yaml#ConfiguredCsv")
            .count { it.value == "configured.yaml#ConfiguredCsv" })
        assertEquals(1, DataFormatOptions.encodings(catalog, "UTF-8").count { it.value == "UTF-8" })
    }


    // Before the catalogue arrives, preserve an existing value but do not invent an implicit default.
    @Test
    fun anAbsentCatalogueOnlyOffersTheCurrentValue() {
        assertEquals(emptyList(), DataFormatOptions.formats(null, "").map { it.value })
        assertEquals(listOf("TSV"), DataFormatOptions.formats(null, "TSV").map { it.value })
        assertEquals(listOf("UTF-16"), DataFormatOptions.encodings(null, "UTF-16").map { it.value })
    }
}
