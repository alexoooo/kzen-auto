package tech.kzen.auto.client.objects.document.common.file

import tech.kzen.auto.common.data.format.FileFormatCatalog
import tech.kzen.auto.common.objects.document.plugin.model.CommonDataEncodingSpec
import tech.kzen.auto.common.objects.document.plugin.model.CommonPluginCoordinate
import tech.kzen.auto.common.objects.document.plugin.model.ReportDefinerDetail
import tech.kzen.lib.platform.ClassName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull


class DataFormatOptionsTest {
    private fun detail(name: String, vararg extensions: String) = ReportDefinerDetail(
        CommonPluginCoordinate(name),
        extensions.toList(),
        CommonDataEncodingSpec.ofString("UTF-8"),
        0,
        ClassName("tech.kzen.auto.plugin.model.record.FlatFileRecord"))


    private val catalog = FileFormatCatalog(
        listOf(detail("CSV", "csv"), detail("Text", "txt")),
        listOf("UTF-8", "ISO-8859-1"))


    @Test
    fun blankIsAnOptionCalledDefaultRatherThanAnEmptyField() {
        val options = DataFormatOptions.formats(catalog, DataFormatOptions.defaultValue)

        assertEquals(DataFormatOptions.defaultValue, options.first().value)
        assertEquals("Default", options.first().label)
        assertEquals(listOf("", "CSV", "Text"), options.map { it.value })
    }


    // The extensions are what tell a reader which format their file is likely to want.
    @Test
    fun aFormatCarriesItsExtensionsAsTheOptionDetail() {
        val csv = DataFormatOptions.formats(catalog, "").single { it.value == "CSV" }

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
        assertEquals(1, DataFormatOptions.formats(catalog, "CSV").count { it.value == "CSV" })
        assertEquals(1, DataFormatOptions.encodings(catalog, "UTF-8").count { it.value == "UTF-8" })
    }


    // Before the catalogue arrives, and if it never does: Default plus whatever is configured, never nothing.
    @Test
    fun anAbsentCatalogueStillOffersDefaultAndTheCurrentValue() {
        assertEquals(listOf(""), DataFormatOptions.formats(null, "").map { it.value })
        assertEquals(listOf("", "TSV"), DataFormatOptions.formats(null, "TSV").map { it.value })
        assertEquals(listOf("", "UTF-16"), DataFormatOptions.encodings(null, "UTF-16").map { it.value })
    }
}
