package tech.kzen.auto.common.serialization

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.common.util.data.DataLocationInfo
import tech.kzen.auto.common.util.storage.StorageAreaInfo
import tech.kzen.auto.common.util.storage.StorageBundleInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.time.Instant


// SER3: the storage / file-listing wire DTOs migrated from hand-written toCollection()/ofCollection() map codecs
// to generated kotlinx codecs. These tests pin the wire form, not just the round-trip — several of the behaviours
// below are load-bearing for SER4 (see storageAreaInfoOmitsNullBudget) or for the retained value-tree codec
// (see dataLocationInfoWireFormMatchesLegacyForStableKeys).
class WireDtoSerializerTest {
    private inline fun <reified T> roundTrip(value: T) {
        val encoded = Json.encodeToString(value)
        val decoded = Json.decodeFromString<T>(encoded)
        assertEquals(value, decoded, "round-trip failed for <$value> (encoded=$encoded)")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun area(budgetBytes: Long? = null) =
        StorageAreaInfo("work", "Work", "Scratch space", 1234L, 4, true, budgetBytes)


    @Test
    fun storageAreaInfoRoundTrip() {
        roundTrip(area())
        roundTrip(area(budgetBytes = 999_000_000L))
        roundTrip(area(budgetBytes = 0L))
    }


    @Test
    fun storageAreaInfoOmitsNullBudget() {
        // LOAD-BEARING. Stock Json has encodeDefaults=false, so `budgetBytes: Long? = null` holding null is skipped
        // entirely and `budget` is absent — matching the legacy codec, which only put the key when non-null.
        // If someone sets explicitNulls=false globally, or drops the `= null` default, this breaks — and the same
        // config carries SER4's LogicStatus.active sentinel-kill (a nullable WITHOUT a default must encode as an
        // explicit JSON null). Guard both here.
        val expected = buildJsonObject {
            put("id", "work")
            put("name", "Work")
            put("description", "Scratch space")
            put("size", 1234L)
            put("bundles", 4)
            put("deletable", true)
        }
        assertEquals(expected, Json.encodeToJsonElement(area()))
    }


    @Test
    fun storageAreaInfoIncludesNonNullBudget() {
        val expected = buildJsonObject {
            put("id", "work")
            put("name", "Work")
            put("description", "Scratch space")
            put("size", 1234L)
            put("bundles", 4)
            put("deletable", true)
            put("budget", 5000L)
        }
        assertEquals(expected, Json.encodeToJsonElement(area(budgetBytes = 5000L)))
    }


    @Test
    fun storageAreaInfoDecodesAbsentBudget() {
        val decoded = Json.decodeFromString<StorageAreaInfo>(
            """{"id":"work","name":"Work","description":"d","size":1,"bundles":0,"deletable":false}""")
        assertNull(decoded.budgetBytes)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun storageBundleInfoRoundTrip() {
        roundTrip(StorageBundleInfo("b1", "Bundle One", 4096L, 1_750_000_000_000L, true))
        roundTrip(StorageBundleInfo("b2", "Bundle Two", 0L, 0L, false))
    }


    @Test
    fun storageBundleInfoWireForm() {
        // size/modified are real JSON numbers and active a real boolean (all three were stringly pre-SER3).
        val expected = buildJsonObject {
            put("key", "b1")
            put("name", "Bundle One")
            put("size", 4096L)
            put("modified", 1_750_000_000_000L)
            put("active", true)
        }
        assertEquals(
            expected,
            Json.encodeToJsonElement(StorageBundleInfo("b1", "Bundle One", 4096L, 1_750_000_000_000L, true)))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun dataLocationRoundTrip() {
        roundTrip(DataLocation.of("C:\\Users\\ao"))
        roundTrip(DataLocation.of("\\\\host\\share\\foo"))
        roundTrip(DataLocation.of("/home/ao"))
        roundTrip(DataLocation.unknown)

        // url-backed. These assert full value equality — which only holds because Url.equals compares the
        // canonical string; it previously delegated to the wrapped platform type and a url-backed DataLocation
        // never equalled a separately-parsed one on JS. See UrlTest.equalsIsValueBased.
        roundTrip(DataLocation.of("https://example.com/data.csv"))
        roundTrip(DataLocation.of("file:///C:/WINDOWS/clock.avi"))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val modifiedFixture = Instant.parse("2026-07-17T12:34:56Z")


    @Test
    fun dataLocationInfoRoundTrip() {
        roundTrip(DataLocationInfo.ofFile(
            DataLocation.of("/home/ao/data.csv"), "data.csv", 2048L, modifiedFixture))
        roundTrip(DataLocationInfo.ofDirectory(
            DataLocation.of("/home/ao"), "ao", modifiedFixture))
        // covers the -1L missing-size sentinel and Instant.DISTANT_PAST
        roundTrip(DataLocationInfo.ofMissingFile(
            DataLocation.of("/home/ao/gone.csv"), "gone.csv"))
    }


    @Test
    fun dataLocationInfoWireForm() {
        val expected = buildJsonObject {
            put("path", "/home/ao/data.csv")
            put("name", "data.csv")
            put("size", 2048L)
            put("modified", "2026-07-17T12:34:56Z")
            put("dir", false)
        }
        assertEquals(
            expected,
            Json.encodeToJsonElement(DataLocationInfo.ofFile(
                DataLocation.of("/home/ao/data.csv"), "data.csv", 2048L, modifiedFixture)))
    }


    @Test
    fun dataLocationInfoWireFormMatchesLegacyForStableKeys() {
        // DataLocationInfo is DUAL-PLANE (2a Bucket C): the retained toCollection() still feeds the value-tree
        // plane via InputBrowserInfo / InputDataInfo. Pin the three keys that must not drift between the two
        // encodings. size/dir intentionally DO diverge (number/boolean on the wire, String in the map form) —
        // the planes never meet, so that is fine, but it should be a deliberate difference, not an accident.
        val info = DataLocationInfo.ofFile(
            DataLocation.of("/home/ao/data.csv"), "data.csv", 2048L, modifiedFixture)

        val legacy = info.toCollection()
        val wire = Json.encodeToJsonElement(info).jsonObject

        for (stableKey in listOf("path", "name", "modified")) {
            assertEquals(
                legacy[stableKey],
                wire[stableKey]?.jsonPrimitive?.content,
                "wire/value-tree drift on '$stableKey'")
        }
    }


    @Test
    fun dataLocationInfoInitCheckSurvivesDecoding() {
        // The init { check(!name.endsWith("/") ...) } runs on decode only because no property has a default —
        // with a default, the plugin's synthetic bitmask constructor would bypass it. Pin that property.
        assertFails {
            Json.decodeFromString<DataLocationInfo>(
                """{"path":"/home/ao","name":"bad/","size":1,"modified":"2026-07-17T12:34:56Z","dir":false}""")
        }
    }
}
