package tech.kzen.auto.server.data

import org.junit.Test
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.model.DataRole
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.data.schema.LegacyDataShapeBridge
import tech.kzen.auto.common.objects.document.plugin.model.CommonDataEncodingSpec
import tech.kzen.auto.common.objects.document.plugin.model.CommonPluginCoordinate
import tech.kzen.auto.server.service.storage.SchemaCacheStorageArea
import tech.kzen.auto.server.objects.datasource.format.ConfiguredDelimitedTestFormats
import tech.kzen.auto.server.util.WorkUtils
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull


class SchemaCacheTest {
    private val format = CommonPluginCoordinate.ofString("CSV")
    private val encoding = CommonDataEncodingSpec.ofString("UTF-8")
    private val shape = LegacyDataShapeBridge.tabular(HeaderListing.ofUnique(listOf("a", "b")))


    @Test
    fun exactKeyIncludesEveryEffectiveDimension() {
        val base = key()
        val variants = listOf(
            key(refId = "other"),
            key(format = CommonPluginCoordinate.ofString("TSV")),
            key(encoding = CommonDataEncodingSpec.ofString("UTF-16")),
            key(size = "11"),
            key(modified = "21"))

        for (variant in variants) {
            assertNotEquals(base.digest(), variant.digest())
        }
    }


    @Test
    fun diskRoundTripDoesNotTurnPeekIntoIo() {
        val work = WorkUtils.temporary("schema-cache-disk")
        SchemaCache(work).put(key(), shape)

        val fresh = SchemaCache(work)
        assertNull(fresh.peek(key()))
        assertEquals(shape, fresh.get(key()))
        assertEquals(shape, fresh.peek(key()))
    }


    @Test
    fun corruptDiskEntryIsAReusableMiss() {
        val work = WorkUtils.temporary("schema-cache-corrupt")
        val cache = SchemaCache(work)
        val bundle = cache.bundleKey(key())
        val shapeFile = work.resolve("${SchemaCache.indexDirName}/$bundle/shape.json")
        Files.createDirectories(shapeFile.parent)
        shapeFile.writeText("{truncated")

        assertNull(cache.get(key()))
        assertNull(cache.get(key()))
    }


    @Test
    fun legacyDiskEntryIsAHarmlessMissAndCanBeReplaced() {
        val work = WorkUtils.temporary("schema-cache-legacy")
        val cache = SchemaCache(work)
        val bundle = cache.bundleKey(key())
        val shapeFile = work.resolve("${SchemaCache.indexDirName}/$bundle/shape.json")
        Files.createDirectories(shapeFile.parent)
        shapeFile.writeText("""{"kind":"tabular","header":["0|a","0|b"]}""")

        assertNull(cache.get(key()))
        cache.put(key(), shape)

        assertEquals(shape, SchemaCache(work).get(key()))
    }


    @Test
    fun unfingerprintedPartHasNoCacheKey() {
        val ref = DataRef(null, "plain.csv")
        val part = configuredTestDataPart(DataRole.main, ref, null)
        assertNull(SchemaCacheKey.of(part))
    }


    @Test
    fun managedDeleteInvalidatesMemoryAndDiskTogether() {
        val work = WorkUtils.temporary("schema-cache-managed")
        val cache = SchemaCache(work)
        cache.put(key(), shape)
        val bundle = cache.bundleKey(key())
        val area = SchemaCacheStorageArea(
            work.resolve(SchemaCache.indexDirName), { false }, cache::invalidate)

        assertNull(area.deleteBundle(bundle))
        assertNull(cache.peek(key()))
        assertFalse(Files.exists(work.resolve("${SchemaCache.indexDirName}/$bundle")))
    }


    private fun key(
        refId: String = "ref",
        format: CommonPluginCoordinate = this.format,
        encoding: CommonDataEncodingSpec = this.encoding,
        size: String = "10",
        modified: String = "20"
    ): SchemaCacheKey = SchemaCacheKey.ofReport(
        DataRef(null, refId, mapOf(
            DataRef.sizeKey to size,
            DataRef.modifiedKey to modified)),
        format,
        encoding)!!
}
