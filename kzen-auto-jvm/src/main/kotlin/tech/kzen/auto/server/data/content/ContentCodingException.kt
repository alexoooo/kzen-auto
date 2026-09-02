package tech.kzen.auto.server.data.content


class ContentCodingException(
    source: String,
    part: String?,
    byteOffset: Long?,
    detail: String,
    cause: Throwable? = null
): ContentDiagnosticException("content coding", source, part, byteOffset, detail, cause)
