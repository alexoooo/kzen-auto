package tech.kzen.auto.common.data.format.detection

import kotlinx.serialization.Serializable
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


@Serializable
data class DetectionPolicy(
    val maximumDecodedBytes: Int,
    val maximumLogicalRecords: Int,
    val timeoutMillis: Long,
    val hintMetadata: List<FormatHintMetadata>,
    val characterEncodings: List<String>
): Digestible {
    init {
        require(maximumDecodedBytes > 0) { "Detection byte limit must be positive" }
        require(maximumLogicalRecords > 0) { "Detection record limit must be positive" }
        require(timeoutMillis > 0) { "Detection timeout must be positive" }
        require(characterEncodings.isNotEmpty()) { "Detection must permit at least one character encoding" }
        require(characterEncodings.none(String::isBlank)) { "Detection character encodings must not be blank" }
    }

    override fun digest(sink: Digest.Sink) {
        sink.addInt(maximumDecodedBytes)
        sink.addInt(maximumLogicalRecords)
        sink.addLong(timeoutMillis)
        sink.addDigestibleList(hintMetadata.sortedBy { it.digest().asString() })
        sink.addInt(characterEncodings.size)
        characterEncodings.forEach(sink::addUtf8)
    }

    companion object {
        const val defaultMaximumDecodedBytes = 256 * 1024
        const val defaultMaximumLogicalRecords = 100
        const val defaultTimeoutMillis = 2_000L

        fun default(hintMetadata: List<FormatHintMetadata>): DetectionPolicy = DetectionPolicy(
            defaultMaximumDecodedBytes,
            defaultMaximumLogicalRecords,
            defaultTimeoutMillis,
            hintMetadata,
            listOf("UTF-8", "windows-1252"))
    }
}
