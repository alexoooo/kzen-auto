package tech.kzen.auto.common.platform

import tech.kzen.auto.platform.Url
import tech.kzen.lib.common.util.digest.Digest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull


private fun digestOf(url: Url): Digest {
    val builder = Digest.Builder()
    url.digest(builder)
    return builder.digest()
}


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


    //-----------------------------------------------------------------------------------------------------------------
    // Identity. This class asserted only toString()/scheme/path/query for years, which is exactly why two equals
    // bugs survived undetected until SER3's serializer round-trip exposed them: Url was an expect/actual over
    // java.net.URI and org.w3c.dom.url.URL, and both actuals delegated equals to the wrapped platform type — one
    // getting reference identity, the other getting equal-but-different-digest. Url is now a single commonMain
    // value class keyed on `location`, so these hold by construction rather than by two actuals remembering to
    // honour a contract. They are kept because they are the invariant, not because it is currently fragile.
    private val identityFixtures = listOf(
        "https://example.com/data.csv",
        "https://example.com/q?a=1&b=2",
        "file:///C:/WINDOWS/clock.avi",
        "file:////server/folder/data.xml",
        "jdbc:h2:file:./work/foo?bar=baz")


    @Test
    fun equalsIsValueBased() {
        for (location in identityFixtures) {
            val a = Url.of(location)
            val b = Url.of(location)

            assertEquals(a, b, "value equality failed for <$location>")
            assertEquals(a.hashCode(), b.hashCode(), "hashCode disagreed with equals for <$location>")
        }
    }


    @Test
    fun equalsAgreesWithDigest() {
        // The invariant: a Url's identity is `location` and digest() hashes that same string, so
        //     a == b  <=>  digest(a) == digest(b)
        // Asserted as a biconditional over every pair rather than as a verdict on a chosen pair — it states the
        // property itself, so it survives any future change to which inputs are considered distinct.
        val fixtures = (identityFixtures + listOf(
            "http://example.com/x",
            "HTTP://example.com/x",
            "https://example.com/a",
            "https://example.com/b"
        )).map { Url.of(it) }

        for (a in fixtures) {
            for (b in fixtures) {
                assertEquals(
                    a == b,
                    digestOf(a) == digestOf(b),
                    "equals/digest disagreement between <$a> and <$b>")
            }
        }
    }


    @Test
    fun locationIsPreservedVerbatim() {
        // There is no normalization: the string given IS the identity. Both of these used to depend on which
        // platform parsed them — js normalized all four, jvm none of them, so they digested differently on
        // client and server.
        assertNotEquals(Url.of("http://example.com/x"), Url.of("HTTP://example.com/x"))
        assertNotEquals(Url.of("http://example.com/x"), Url.of("http://EXAMPLE.com/x"))
        assertNotEquals(Url.of("http://example.com/x"), Url.of("http://example.com:80/x"))
        assertNotEquals(Url.of("http://example.com/b"), Url.of("http://example.com/a/../b"))

        assertEquals("HTTP://example.com/x", Url.of("HTTP://example.com/x").toString())
        assertEquals("http://example.com/a/../b", Url.of("http://example.com/a/../b").toString())
    }


    @Test
    fun spaceIsAcceptedRatherThanRejected() {
        // java.net.URI rejected a literal space outright while org.w3c.dom.url.URL %-encoded it, so this url
        // parsed on the client and failed on the server. One implementation, one answer — and it is kept
        // verbatim, like every other location.
        val location = "http://example.com/a b"
        assertEquals(location, Url.of(location).toString())
    }


    @Test
    fun windowsDriveIsNotAUrl() {
        // A single-letter scheme is rejected so that a Windows drive is never mistaken for a url. DataLocation
        // .parse tries FilePath.parse first, so this is belt-and-braces, but Url.parse is public.
        assertNull(Url.parse("C:\\Users\\ao\\data.csv"))
        assertNull(Url.parse("C:/Users/ao/data.csv"))
        assertNull(Url.parse("data.csv"))
        assertNull(Url.parse(""))
        assertNull(Url.parse("://nope"))
    }


    @Test
    fun distinctUrlsAreNotEqual() {
        assertNotEquals(Url.of("https://example.com/a"), Url.of("https://example.com/b"))
        assertNotEquals(Url.of("https://example.com/a"), Url.of("https://other.com/a"))
        assertNotEquals(Url.of("file:///C:/x"), Url.of("file:////C:/x"))
    }


    @Test
    fun deduplicatesInASet() {
        // The consequence that actually bit: with reference-identity equals on JS, a Set<Url> silently kept
        // duplicates and a Map<Url, _> lookup always missed.
        val set = identityFixtures.map { Url.of(it) }.toSet() + identityFixtures.map { Url.of(it) }.toSet()
        assertEquals(identityFixtures.size, set.size, "Set<Url> failed to deduplicate equal values")

        val map = mutableMapOf(Url.of("https://example.com/k") to "v")
        assertEquals("v", map[Url.of("https://example.com/k")], "Map<Url, _> lookup missed an equal key")
    }
}