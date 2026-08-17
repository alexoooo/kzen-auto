package tech.kzen.auto.common.util.data

import tech.kzen.auto.platform.Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull


class DataLocationTest {
    @Test
    fun parseBackslashRootDriveC() {
        val dataLocation = DataLocation.of("C:\\")
        assertEquals(FilePath.of("C:/"), dataLocation.filePath)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun parentStaysWithinTheHeldKind() {
        assertEquals(
            DataLocation.ofFile(FilePath.of("C:/Users")),
            DataLocation.of("C:\\Users\\ao").parent())

        assertEquals(
            DataLocation.ofUrl(Url.of("https://example.com/a")),
            DataLocation.of("https://example.com/a/b").parent())
    }


    @Test
    fun unknownHasNoParentAndIsItsOwnAncestry() {
        assertNull(DataLocation.unknown.parent())
        assertEquals(listOf(DataLocation.unknown), DataLocation.unknown.ancestors())
    }


    @Test
    fun urlAncestorsStopAtTheHost() {
        val ancestors = DataLocation.of("https://example.com/a/b").ancestors()

        assertEquals(
            listOf("https://example.com", "https://example.com/a", "https://example.com/a/b"),
            ancestors.map { it.asString() })
    }


    @Test
    fun rootIsItsOwnAncestry() {
        assertEquals(listOf("C:/"), DataLocation.of("C:\\").ancestors().map { it.asString() })
        assertEquals(listOf("/"), DataLocation.of("/").ancestors().map { it.asString() })
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Each ancestry is asserted alongside the segment name it yields, because fileName() delegates to the held
    // value object and the roots are exactly where the two disagree about what a "last segment" is.
    @Test
    fun windowsAncestors() {
        val parts = DataLocation.of("C:\\Users\\ao").ancestors()

        assertEquals(3, parts.size)
        assertEquals("C:/", parts[0].asString())
        assertEquals("C:", parts[0].fileName())
        assertEquals("C:/Users", parts[1].asString())
        assertEquals("Users", parts[1].fileName())
        assertEquals("C:/Users/ao", parts[2].asString())
        assertEquals("ao", parts[2].fileName())
    }


    @Test
    fun windowsAncestorsWithTrailer() {
        val parts = DataLocation.of("C:\\//Users\\\\ao\\").ancestors()

        assertEquals(3, parts.size)
        assertEquals("C:/", parts[0].asString())
        assertEquals("C:", parts[0].fileName())
        assertEquals("C:/Users", parts[1].asString())
        assertEquals("Users", parts[1].fileName())
        assertEquals("C:/Users/ao", parts[2].asString())
        assertEquals("ao", parts[2].fileName())
    }


    @Test
    fun windowsNetworkAncestors() {
        val parts = DataLocation.of("\\\\hostname\\share/foo").ancestors()

        assertEquals(3, parts.size)
        assertEquals("\\\\hostname", parts[0].asString())
        // A bare host is not a share, so it has no segment to strip and names itself whole.
        assertEquals("\\\\hostname", parts[0].fileName())
        assertEquals("\\\\hostname\\share", parts[1].asString())
        assertEquals("share", parts[1].fileName())
        assertEquals("\\\\hostname\\share/foo", parts[2].asString())
        assertEquals("foo", parts[2].fileName())
    }


    @Test
    fun unixAncestors() {
        val parts = DataLocation.of("/home/ao").ancestors()

        assertEquals(3, parts.size)
        assertEquals("/", parts[0].asString())
        assertEquals("", parts[0].fileName())
        assertEquals("/home", parts[1].asString())
        assertEquals("home", parts[1].fileName())
        assertEquals("/home/ao", parts[2].asString())
        assertEquals("ao", parts[2].fileName())
    }


    @Test
    fun unixAncestorsWithTrailer() {
        val parts = DataLocation.of("/home///ao/").ancestors()

        assertEquals(3, parts.size)
        assertEquals("/", parts[0].asString())
        assertEquals("", parts[0].fileName())
        assertEquals("/home", parts[1].asString())
        assertEquals("home", parts[1].fileName())
        assertEquals("/home/ao", parts[2].asString())
        assertEquals("ao", parts[2].fileName())
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun fileNameFollowsTheHeldKind() {
        assertEquals("data.csv", DataLocation.of("https://example.com/a/data.csv").fileName())
        assertEquals("unknown", DataLocation.unknown.fileName())
    }
}
