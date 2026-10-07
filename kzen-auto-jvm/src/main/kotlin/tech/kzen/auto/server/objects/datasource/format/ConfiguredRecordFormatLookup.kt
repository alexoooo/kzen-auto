package tech.kzen.auto.server.objects.datasource.format

import tech.kzen.auto.common.data.format.ConfiguredRecordFormat
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.AttributeLocation


fun interface ConfiguredRecordFormatLookup {
    companion object {
        /** The attribute through which a reader (a File source, Parse) is given its format. */
        val formatAttributePath = AttributePath.ofName(AttributeName("format"))
    }


    suspend fun preflight(reference: String): ConfiguredRecordFormatPreflight

    /**
     * [format] under the graph coordinate the attribute that injected it ([injectedBy]) references. Null when that
     * attribute is not in the graph (a programmatic or test-local owner), which then reads through [format] alone.
     * Never inferred from the format's value: two formats with the same settings are still two coordinates.
     */
    suspend fun preflight(
        format: ConfiguredRecordFormat,
        injectedBy: AttributeLocation
    ): ConfiguredRecordFormatPreflight? = null
}
