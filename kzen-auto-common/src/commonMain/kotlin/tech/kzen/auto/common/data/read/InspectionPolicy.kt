package tech.kzen.auto.common.data.read

import kotlinx.serialization.Serializable
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


@Serializable
data class InspectionPolicy(
    val maximumRecords: Int? = null,
    val maximumExpandedBytes: Long? = null,
    val timeoutMillis: Long? = null
): Digestible {
    init {
        requirePositive(maximumRecords, "maximumRecords")
        requirePositive(maximumExpandedBytes, "maximumExpandedBytes")
        requirePositive(timeoutMillis, "timeoutMillis")
    }

    override fun digest(sink: Digest.Sink) {
        sink.addUtf8Nullable(maximumRecords?.toString())
        sink.addUtf8Nullable(maximumExpandedBytes?.toString())
        sink.addUtf8Nullable(timeoutMillis?.toString())
    }
}
