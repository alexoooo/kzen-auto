package tech.kzen.auto.common.util.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue


private fun parentOf(location: String): String? {
    return FilePath.of(location).parent()?.location
}


class FilePathTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun windowsParentClimbsToTheDriveRoot() {
        assertEquals("C:/Users", parentOf("C:\\Users\\ao"))
        assertEquals("C:/", parentOf("C:\\Users"))
        assertNull(parentOf("C:\\"))
    }


    @Test
    fun unixParentClimbsToTheRoot() {
        assertEquals("/home", parentOf("/home/ao"))
        assertEquals("/", parentOf("/home"))
        assertNull(parentOf("/"))
    }


    @Test
    fun networkParentClimbsShareThenHost() {
        assertEquals("\\\\host\\share", parentOf("\\\\host\\share/data"))
        assertEquals("\\\\host", parentOf("\\\\host\\share"))
        assertNull(parentOf("\\\\host"))
    }


    @Test
    fun relativeParentEndsAtASingleSegment() {
        assertEquals("a", parentOf("a/b"))
        assertNull(parentOf("data.csv"))
    }


    @Test
    fun trailingAndRepeatedSeparatorsDoNotBecomeSegments() {
        assertEquals("C:/Users", parentOf("C:\\//Users\\\\ao\\"))
        assertEquals("/home", parentOf("/home///ao/"))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun parseBackslashRootDriveC() {
        val filePath = FilePath.of("C:\\")
        assertTrue(filePath.isRoot())
        assertEquals(FilePathType.AbsoluteWindows, filePath.type)
        assertEquals("C:/", filePath.location)
    }


    @Test
    fun parseSlashRootDriveC() {
        val filePath = FilePath.of("C:/")
        assertTrue(filePath.isRoot())
        assertEquals(FilePathType.AbsoluteWindows, filePath.type)
        assertEquals("C:/", filePath.location)
    }


    @Test
    fun parseRelativeLetterC() {
        val filePath = FilePath.of("C")
        assertFalse(filePath.isRoot())
        assertEquals(FilePathType.Relative, filePath.type)
        assertEquals("C", filePath.location)
    }


    @Test
    fun parseRawRootDriveC() {
        val filePath = FilePath.of("C:")
        assertTrue(filePath.isRoot())
        assertEquals(FilePathType.AbsoluteWindows, filePath.type)
        assertEquals("C:/", filePath.location)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun fileNameIsTheLastSegment() {
        assertEquals("data.csv", FilePath.of("C:/Users/ao/data.csv").fileName())
        assertEquals("ao", FilePath.of("/home/ao").fileName())
        assertEquals("data.csv", FilePath.of("data.csv").fileName())
    }


    @Test
    fun fileNameOfADriveRootDropsTheSeparator() {
        // `C:` alone is not a path, so parse() keeps the separator that fileName() then sheds.
        assertEquals("C:", FilePath.of("C:/").fileName())
        assertEquals("C:", FilePath.of("C:").fileName())
    }


    @Test
    fun fileNameOfTheUnixRootIsEmpty() {
        assertEquals("", FilePath.of("/").fileName())
    }


    @Test
    fun fileNameCrossesTheNetworkPrefixOnlyForAShare() {
        assertEquals("share", FilePath.of("\\\\hostname\\share").fileName())
        // A bare host has no share to name, so it answers with itself rather than the hostname alone.
        assertEquals("\\\\hostname", FilePath.of("\\\\hostname").fileName())
        assertEquals("foo", FilePath.of("\\\\hostname\\share/foo").fileName())
    }
}