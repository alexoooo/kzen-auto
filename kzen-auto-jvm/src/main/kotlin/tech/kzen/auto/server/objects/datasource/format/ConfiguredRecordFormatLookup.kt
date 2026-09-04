package tech.kzen.auto.server.objects.datasource.format

import tech.kzen.auto.common.data.format.ConfiguredRecordFormat


fun interface ConfiguredRecordFormatLookup {
    suspend fun preflight(reference: String): ConfiguredRecordFormatPreflight

    /** Resolves the graph coordinate of an injected configured-format instance when one is available. */
    suspend fun preflight(format: ConfiguredRecordFormat): ConfiguredRecordFormatPreflight? = null
}
