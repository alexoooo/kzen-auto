package tech.kzen.auto.util

import org.junit.Test
import java.nio.file.Path
import kotlin.test.assertEquals


class AutoJvmUtilsTest
{
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun cDriveParse() {
        val parsed = AutoJvmUtils.parsePath("C:")
        assertEquals(Path.of("C:\\"), parsed)
    }


    @Test
    fun cFolderParse() {
        val parsed = AutoJvmUtils.parsePath("C:\\foo/bar")
        assertEquals(Path.of("C:\\foo\\bar"), parsed)
    }
}