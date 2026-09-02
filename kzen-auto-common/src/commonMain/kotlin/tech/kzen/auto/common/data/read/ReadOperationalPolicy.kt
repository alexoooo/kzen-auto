package tech.kzen.auto.common.data.read

import kotlinx.serialization.Serializable
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


@Serializable
data class ReadOperationalPolicy(
    val maximumExpandedBytes: Long? = null,
    val maximumRecordCharacters: Int? = null,
    val maximumFieldCharacters: Int? = null,
    val maximumFields: Int? = null,
    val timeoutMillis: Long? = null
): Digestible {
    init {
        requirePositive(maximumExpandedBytes, "maximumExpandedBytes")
        requirePositive(maximumRecordCharacters, "maximumRecordCharacters")
        requirePositive(maximumFieldCharacters, "maximumFieldCharacters")
        requirePositive(maximumFields, "maximumFields")
        requirePositive(timeoutMillis, "timeoutMillis")
    }

    override fun digest(sink: Digest.Sink) {
        sink.addUtf8Nullable(maximumExpandedBytes?.toString())
        sink.addUtf8Nullable(maximumRecordCharacters?.toString())
        sink.addUtf8Nullable(maximumFieldCharacters?.toString())
        sink.addUtf8Nullable(maximumFields?.toString())
        sink.addUtf8Nullable(timeoutMillis?.toString())
    }
}
