package tech.kzen.auto.common.data.format

import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.read.ContentCodingSpec
import tech.kzen.auto.common.data.read.ResolvedReadSpec
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.lib.common.util.digest.Digest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue


class FileFormatNamesTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val csv = NamedFormat("CSV", listOf("csv"))
    private val archive = NamedFormat("Archive listing", listOf("tar"))
    private val text = NamedFormat("Plain text", listOf("txt", ".LOG"))
    private val formats = listOf(csv, archive, text)


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun fileNameIsTheLastSegmentOfEitherSeparator() {
        assertEquals("orders.csv", FileFormatNames.fileName("/data/in/orders.csv"))
        assertEquals("orders.csv", FileFormatNames.fileName("C:\\data\\in\\orders.csv"))
        assertEquals("orders.csv", FileFormatNames.fileName("orders.csv"))
    }


    @Test
    fun gzipFollowsTheGzAndTgzSuffixesOnly() {
        assertEquals(ContentCodingSpec.gzip, FileFormatNames.contentCoding("/data/orders.csv.gz"))
        assertEquals(ContentCodingSpec.gzip, FileFormatNames.contentCoding("/data/ORDERS.CSV.GZ"))
        assertEquals(ContentCodingSpec.gzip, FileFormatNames.contentCoding("/data/input.tgz"))
        assertEquals(ContentCodingSpec.identity, FileFormatNames.contentCoding("/data/orders.csv"))
        assertEquals(ContentCodingSpec.identity, FileFormatNames.contentCoding("/data/input.tar"))
        assertEquals(ContentCodingSpec.identity, FileFormatNames.contentCoding("/data.gz/orders.csv"))
    }


    @Test
    fun formatExtensionIsUnderTheGzipSuffix() {
        assertEquals("csv", FileFormatNames.formatExtension("orders.csv"))
        assertEquals("csv", FileFormatNames.formatExtension("Orders.CSV.gz"))
        assertEquals("tar", FileFormatNames.formatExtension("input.tar.gz"))
        assertEquals("tar", FileFormatNames.formatExtension("input.tgz"))
        assertEquals("gz", FileFormatNames.formatExtension("data.gz.gz"))
    }


    @Test
    fun aNameWithoutAnExtensionHasNoFormatExtension() {
        assertNull(FileFormatNames.formatExtension("README"))
        assertNull(FileFormatNames.formatExtension("data.gz"))
        assertNull(FileFormatNames.formatExtension(".profile"))
        assertNull(FileFormatNames.formatExtension("trailing."))
        assertNull(FileFormatNames.formatExtension("/dir.d/README"))
    }


    @Test
    fun declaredExtensionsMatchIgnoringCaseAndALeadingDot() {
        assertTrue(FileFormatNames.names(text.extensions, "server.log"))
        assertTrue(FileFormatNames.names(text.extensions, "NOTES.TXT"))
        assertFalse(FileFormatNames.names(text.extensions, "orders.csv"))
        assertFalse(FileFormatNames.names(text.extensions, "README"))
    }


    @Test
    fun theOneFormatNamingTheExtensionIsChosen() {
        assertEquals(FileFormatNames.Choice.Named(csv), FileFormatNames.choose("/in/orders.csv.gz", formats))
        assertEquals(FileFormatNames.Choice.Named(archive), FileFormatNames.choose("/in/input.tgz", formats))
        assertEquals(FileFormatNames.Choice.Named(text), FileFormatNames.choose("/in/server.log", formats))
    }


    @Test
    fun noFormatIsChosenWithoutExactlyOneNamingTheExtension() {
        val missing = assertIs<FileFormatNames.Choice.Unnamed>(FileFormatNames.choose("/in/README", formats))
        assertEquals("'README' has no extension to name its format; select a format", missing.reason)

        val unknown = assertIs<FileFormatNames.Choice.Unnamed>(FileFormatNames.choose("/in/data.bin", formats))
        assertEquals("No format reads '.bin' files such as 'data.bin'; select a format", unknown.reason)

        val rival = NamedFormat("Twin CSV", listOf("csv"))
        val ambiguous = assertIs<FileFormatNames.Choice.Unnamed>(
            FileFormatNames.choose("/in/orders.csv", formats + rival))
        assertEquals(
            "'.csv' is named by more than one format (CSV, Twin CSV); select a format for 'orders.csv'",
            ambiguous.reason)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private class NamedFormat(
        override val title: String,
        override val extensions: List<String>
    ): ConfiguredRecordFormat {
        override val catalogVisible = true

        @Suppress("OVERRIDE_DEPRECATION")
        override fun resolvedRead(ref: DataRef): ResolvedReadSpec = throw UnsupportedOperationException()

        override fun declaredShape(): DataShape? = null

        override fun digest(sink: Digest.Sink) {
            sink.addUtf8(title)
        }
    }
}
