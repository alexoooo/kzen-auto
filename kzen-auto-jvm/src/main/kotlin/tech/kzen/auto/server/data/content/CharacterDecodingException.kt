package tech.kzen.auto.server.data.content


class CharacterDecodingException(
    source: String,
    part: String?,
    byteOffset: Long?,
    detail: String,
    cause: Throwable? = null
): ContentDiagnosticException("character decoding", source, part, byteOffset, detail, cause)
