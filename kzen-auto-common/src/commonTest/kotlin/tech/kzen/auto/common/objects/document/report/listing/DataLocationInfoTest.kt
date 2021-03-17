package tech.kzen.auto.common.objects.document.report.listing

import tech.kzen.auto.common.util.data.DataLocation
import kotlin.test.Test
import kotlin.test.assertEquals


class DataLocationInfoTest {
    @Test
    fun windowsAncestors() {
        val filePath = "C:\\Users\\ao"
        val dataLocation = DataLocation.parse(filePath)!!
        val parts = dataLocation.ancestors()

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
        val filePath = "C:\\//Users\\\\ao\\"
        val dataLocation = DataLocation.parse(filePath)!!
        val parts = dataLocation.ancestors()

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
        val filePath = "\\\\hostname\\share/foo"
        val dataLocation = DataLocation.parse(filePath)!!
        val parts = dataLocation.ancestors()

        assertEquals(3, parts.size)
        assertEquals("\\\\hostname", parts[0].asString())
        assertEquals("\\\\hostname", parts[0].fileName())
        assertEquals("\\\\hostname\\share", parts[1].asString())
        assertEquals("share", parts[1].fileName())
        assertEquals("\\\\hostname\\share/foo", parts[2].asString())
        assertEquals("foo", parts[2].fileName())
    }


    @Test
    fun unixAncestors() {
        val filePath = "/home/ao"
        val dataLocation = DataLocation.parse(filePath)!!
        val parts = dataLocation.ancestors()

        assertEquals(3, parts.size)
        assertEquals("/", parts[0].asString())
        assertEquals("", parts[0].fileName())
        assertEquals("/home", parts[1].asString())
        assertEquals("home", parts[1].fileName())
        assertEquals(filePath, parts[2].asString())
        assertEquals("ao", parts[2].fileName())
    }


    @Test
    fun unixAncestorsWithTrailer() {
        val filePath = "/home///ao/"
        val dataLocation = DataLocation.parse(filePath)!!
        val parts = dataLocation.ancestors()

        assertEquals(3, parts.size)
        assertEquals("/", parts[0].asString())
        assertEquals("", parts[0].fileName())
        assertEquals("/home", parts[1].asString())
        assertEquals("home", parts[1].fileName())
        assertEquals("/home/ao", parts[2].asString())
        assertEquals("ao", parts[2].fileName())
    }
}