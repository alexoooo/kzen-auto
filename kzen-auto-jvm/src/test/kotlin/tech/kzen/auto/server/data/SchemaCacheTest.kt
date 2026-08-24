package tech.kzen.auto.server.data

import org.junit.Test
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.model.DataRole
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.objects.document.plugin.model.CommonDataEncodingSpec
import tech.kzen.auto.common.objects.document.plugin.model.CommonPluginCoordinate
import tech.kzen.auto.server.service.storage.SchemaCacheStorageArea
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
    private val shape = DataShape.Tabular(HeaderListing.ofUnique(listOf("a", "b")))


    @Test
    fun exactKeyIncludesEveryEffectiveDimension() {
        val base = key()
        val variants = listOf(
            base.copy(refId = "other"),
            base.copy(format = CommonPluginCoordinate.ofString("TSV")),
            base.copy(encoding = CommonDataEncodingSpec.ofString("UTF-16")),
            base.copy(size = "11"),
            base.copy(modified = "21"))

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
    fun unfingerprintedPartHasNoCacheKey() {
        val part = DataPart(DataRole.main, DataRef(null, "plain.csv"), null, null)
        assertNull(SchemaCacheKey.of(part, format, encoding))
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


    private fun key() = SchemaCacheKey("ref", format, encoding, "10", "20")
}
