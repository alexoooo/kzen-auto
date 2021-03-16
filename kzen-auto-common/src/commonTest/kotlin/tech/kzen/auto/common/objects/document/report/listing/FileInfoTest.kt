package tech.kzen.auto.common.objects.document.report.listing

import tech.kzen.auto.platform.DataLocation
import kotlin.test.Test
import kotlin.test.assertEquals


class FileInfoTest {
    @Test
    fun splitPartsWindows() {
        val filePath = "C:\\Users\\ao"
        val parts = FileInfo.split(DataLocation.ofUri(filePath))
        assertEquals(3, parts.size)
        assertEquals("C:", parts[0].first)
        assertEquals("C:", parts[0].second)
        assertEquals("C:\\Users", parts[1].first)
        assertEquals("Users", parts[1].second)
        assertEquals(filePath, parts[2].first)
        assertEquals("ao", parts[2].second)
    }


    @Test
    fun splitPartsUnix() {
        val filePath = "/home/ao"
        val parts = FileInfo.split(DataLocation.ofUri(filePath))
        assertEquals(2, parts.size)
        assertEquals("/home", parts[0].first)
        assertEquals("home", parts[0].second)
        assertEquals(filePath, parts[1].first)
        assertEquals("ao", parts[1].second)
    }
}