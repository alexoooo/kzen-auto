package tech.kzen.auto.common.data.format.detection

import kotlinx.serialization.Serializable
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


@Serializable
@ConsistentCopyVisibility
data class NormalizedFormatHints private constructor(
    val filenameExtension: String?,
    val mediaType: String?,
    val providerHints: Map<String, String>
): Digestible {
    override fun digest(sink: Digest.Sink) {
        sink.addUtf8Nullable(filenameExtension)
        sink.addUtf8Nullable(mediaType)
        sink.addInt(providerHints.size)
        for (key in providerHints.keys.sorted()) {
            sink.addUtf8(key)
            sink.addUtf8(providerHints.getValue(key))
        }
    }

    companion object {
        val empty = of()

        fun of(
            filenameExtension: String? = null,
            mediaType: String? = null,
            providerHints: Map<String, String> = emptyMap()
        ): NormalizedFormatHints {
            val normalizedProviderHints = linkedMapOf<String, String>()
            for ((key, value) in providerHints.entries.sortedBy { it.key.trim().lowercase() }) {
                val normalizedKey = key.trim().lowercase()
                require(normalizedKey.isNotEmpty()) { "Provider-hint name must not be blank" }
                normalizedProviderHints[normalizedKey] = value.trim()
            }
            return NormalizedFormatHints(
                filenameExtension?.trim()?.removePrefix(".")?.lowercase()?.takeIf(String::isNotEmpty),
                mediaType?.substringBefore(';')?.trim()?.lowercase()?.takeIf(String::isNotEmpty),
                normalizedProviderHints)
        }
    }
}
