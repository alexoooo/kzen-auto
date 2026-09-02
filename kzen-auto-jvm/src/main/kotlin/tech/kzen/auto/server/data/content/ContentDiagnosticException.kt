package tech.kzen.auto.server.data.content


open class ContentDiagnosticException(
    val stage: String,
    val source: String,
    val part: String?,
    val byteOffset: Long? = null,
    detail: String,
    cause: Throwable? = null
): IllegalArgumentException(message(stage, source, part, byteOffset, detail), cause) {
    companion object {
        private fun message(stage: String, source: String, part: String?, byteOffset: Long?, detail: String): String {
            val location = if (part == null) source else "$source (part $part)"
            val offset = byteOffset?.let { " at byte $it" } ?: ""
            return "$stage failure for $location$offset: $detail"
        }
    }
}
