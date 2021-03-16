package tech.kzen.auto.server.objects.report.pipeline.input.v2.model

import tech.kzen.auto.common.objects.document.report.listing.DataLocation
import tech.kzen.auto.plugin.spec.DataEncodingSpec
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


// TODO: better name
data class DataLocationInfo(
    val dataLocation: DataLocation,
    val dataEncoding: DataEncodingSpec
): Digestible {
    companion object {
        val literalUtf8 = DataLocationInfo(DataLocation.unknown, DataEncodingSpec.utf8)
    }


    override fun digest(builder: Digest.Builder) {
        builder.addDigestible(dataLocation)
        builder.addUtf8Nullable(dataEncoding.textEncoding?.charset?.displayName())
    }
}