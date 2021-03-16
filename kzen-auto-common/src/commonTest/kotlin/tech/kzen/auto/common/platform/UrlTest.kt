package tech.kzen.auto.common.platform

import tech.kzen.auto.platform.Url
import kotlin.test.Test
import kotlin.test.assertEquals


// see: https://en.wikipedia.org/wiki/File_URI_scheme
class UrlTest {
    @Test
    fun google() {
        val location = "https://www.google.com/"
        assertEquals(location, Url.of(location).toString())
    }


    @Test
    fun windowsAbsoluteFile() {
        val location = "file:///C:/WINDOWS/clock.avi"
        val url = Url.of(location)

        assertEquals(location, url.toString())
        assertEquals("file", url.scheme)
        assertEquals("/C:/WINDOWS/clock.avi", url.path)
    }


    @Test
    fun windowsNetworkFile() {
        val location = "file:////server/folder/data.xml"
        val url = Url.of(location)

        assertEquals(location, url.toString())
        assertEquals("file", url.scheme)
        assertEquals("//server/folder/data.xml", url.path)
    }


    @Test
    fun dbWithQuery() {
        val location = "jdbc:h2:file:./work/foo?bar=baz"
        val url = Url.of(location)

        assertEquals(location, url.toString())
        assertEquals("jdbc", url.scheme)
        assertEquals("h2:file:./work/foo", url.path)
        assertEquals("bar=baz", url.query)
    }
}