package tech.kzen.auto.common.util.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class FilePathTest {
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
}