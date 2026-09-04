package tech.kzen.auto.common.data.format.detection

import kotlinx.serialization.Serializable
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


@Serializable
data class FormatHintMetadata(
    val hintClass: FormatHintClass,
    val extensions: List<String>,
    val mediaTypes: List<String>,
    val structuredFamily: String? = null
): Digestible {
    init {
        require(extensions == normalizeExtensions(extensions)) { "Format-hint extensions must be normalized" }
        require(mediaTypes == normalizeMediaTypes(mediaTypes)) { "Format-hint media types must be normalized" }
        require(hintClass == FormatHintClass.StructuredFamily || structuredFamily == null) {
            "Only a structured hint may name a structured family"
        }
        require(hintClass != FormatHintClass.StructuredFamily || !structuredFamily.isNullOrBlank()) {
            "A structured hint must name its family"
        }
        require(structuredFamily == structuredFamily?.trim()?.lowercase()) {
            "Structured hint family must be normalized"
        }
    }

    override fun digest(sink: Digest.Sink) {
        sink.addUtf8(hintClass.name)
        sink.addInt(extensions.size)
        extensions.forEach(sink::addUtf8)
        sink.addInt(mediaTypes.size)
        mediaTypes.forEach(sink::addUtf8)
        sink.addUtf8Nullable(structuredFamily)
    }

    companion object {
        fun structured(
            family: String,
            extensions: List<String>,
            mediaTypes: List<String> = emptyList()
        ): FormatHintMetadata = FormatHintMetadata(
            FormatHintClass.StructuredFamily,
            normalizeExtensions(extensions),
            normalizeMediaTypes(mediaTypes),
            family.trim().lowercase())

        fun genericText(
            extensions: List<String>,
            mediaTypes: List<String> = emptyList()
        ): FormatHintMetadata = FormatHintMetadata(
            FormatHintClass.GenericText,
            normalizeExtensions(extensions),
            normalizeMediaTypes(mediaTypes))

        fun semanticText(
            extensions: List<String>,
            mediaTypes: List<String> = emptyList()
        ): FormatHintMetadata = FormatHintMetadata(
            FormatHintClass.SemanticText,
            normalizeExtensions(extensions),
            normalizeMediaTypes(mediaTypes))

        private fun normalizeExtensions(values: List<String>): List<String> = values
            .map { it.trim().removePrefix(".").lowercase() }
            .filter(String::isNotEmpty)
            .distinct()
            .sorted()

        private fun normalizeMediaTypes(values: List<String>): List<String> = values
            .map { it.substringBefore(';').trim().lowercase() }
            .filter(String::isNotEmpty)
            .distinct()
            .sorted()
    }
}
