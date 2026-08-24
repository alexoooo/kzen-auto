package tech.kzen.auto.server.data

import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.objects.document.plugin.model.CommonDataEncodingSpec
import tech.kzen.auto.common.objects.document.plugin.model.CommonPluginCoordinate


/** Report-facing schema-cache adapter. The extractor is never invoked on an exact cache hit. */
class ColumnListingAction(
    private val schemaCache: SchemaCache
) {
    fun headerListing(
        part: DataPart,
        effectiveFormat: CommonPluginCoordinate,
        effectiveEncoding: CommonDataEncodingSpec,
        extract: () -> HeaderListing
    ): HeaderListing {
        val key = SchemaCacheKey.of(part, effectiveFormat, effectiveEncoding)
        val cached = key?.let(schemaCache::get)
        if (cached != null) {
            return (cached as? DataShape.Tabular)?.header
                ?: error("File schema cache entry is not tabular: $cached")
        }

        val header = extract()
        if (key != null) {
            schemaCache.put(key, DataShape.Tabular(header))
        }
        return header
    }
}
