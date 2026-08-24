package tech.kzen.auto.server.data

import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.objects.document.plugin.model.CommonDataEncodingSpec
import tech.kzen.auto.common.objects.document.plugin.model.CommonPluginCoordinate
import tech.kzen.lib.common.util.digest.Digest


/** Exact schema identity after format and encoding defaults have been resolved. */
data class SchemaCacheKey(
    val refId: String,
    val format: CommonPluginCoordinate,
    val encoding: CommonDataEncodingSpec,
    val size: String,
    val modified: String
) {
    companion object {
        fun of(
            part: DataPart,
            effectiveFormat: CommonPluginCoordinate,
            effectiveEncoding: CommonDataEncodingSpec
        ): SchemaCacheKey? {
            val fingerprint = part.ref.fingerprintOrNull()
                ?: return null
            return SchemaCacheKey(
                part.ref.id, effectiveFormat, effectiveEncoding,
                fingerprint.first, fingerprint.second)
        }
    }


    fun digest(): Digest = Digest.build {
        addUtf8(refId)
        addDigestible(format)
        addUtf8(encoding.asString())
        addUtf8(size)
        addUtf8(modified)
    }
}
