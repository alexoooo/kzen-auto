package tech.kzen.auto.common.util.data

import kotlin.test.Test
import kotlin.test.assertEquals


class DataLocationTest {
    @Test
    fun parseBackslashRootDriveC() {
        val dataLocation = DataLocation.of("C:\\")
        assertEquals(FilePath.of("C:/"), dataLocation.filePath)
    }
}