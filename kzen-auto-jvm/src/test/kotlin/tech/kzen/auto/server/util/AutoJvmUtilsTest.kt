package tech.kzen.auto.server.util

import org.junit.Test
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals


class AutoJvmUtilsTest
{
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun cDriveParse() {
        val parsed = AutoJvmUtils.parsePath("C:")
        assertEquals(Path.of("C:" + File.separator), parsed)
    }


    @Test
    fun cFolderParse() {
        val parsed = AutoJvmUtils.parsePath("C:\\foo/bar")
        assertEquals(Path.of("C:${File.separator}foo${File.separator}bar"), parsed)
    }
}