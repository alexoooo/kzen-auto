package tech.kzen.auto.server.data

import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.data.schema.LegacyDataShapeBridge
import tech.kzen.auto.common.objects.document.plugin.model.CommonDataEncodingSpec
import tech.kzen.auto.common.objects.document.plugin.model.CommonPluginCoordinate


/** Report-facing schema-cache adapter. The extractor is never invoked on an exact cache hit. */
class ColumnListingAction(
    private val schemaCache: SchemaCache
) {
    fun headerListing(
        ref: DataRef,
        effectiveFormat: CommonPluginCoordinate,
        effectiveEncoding: CommonDataEncodingSpec,
        extract: () -> HeaderListing
    ): HeaderListing {
        val key = SchemaCacheKey.ofReport(ref, effectiveFormat, effectiveEncoding)
        val cached = key?.let(schemaCache::get)
        if (cached != null) {
            return LegacyDataShapeBridge.headerOrNull(cached)
                ?: error("File schema cache entry is not tabular: $cached")
        }

        val header = extract()
        if (key != null) {
            schemaCache.put(key, LegacyDataShapeBridge.tabular(header))
        }
        return header
    }
}
