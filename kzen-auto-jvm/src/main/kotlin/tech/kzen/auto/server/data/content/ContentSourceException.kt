package tech.kzen.auto.server.data.content


class ContentSourceException(
    source: String,
    part: String?,
    detail: String,
    cause: Throwable? = null
): ContentDiagnosticException("source", source, part, detail = detail, cause = cause)
