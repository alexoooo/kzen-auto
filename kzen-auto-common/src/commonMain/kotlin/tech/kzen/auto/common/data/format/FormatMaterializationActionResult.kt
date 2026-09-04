package tech.kzen.auto.common.data.format

import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.NullExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue


data class FormatMaterializationActionResult(
    val formatReference: String,
    val formatBody: MapExecutionValue,
    val schemaReference: String?,
    val schemaBody: MapExecutionValue?,
    val editor: FormatOverrideEditorMetadata?,
    val encoding: String?
) {
    init {
        require(formatReference.isNotBlank()) { "Materialized format reference must not be blank" }
        require((schemaReference == null) == (schemaBody == null)) {
            "Materialized schema reference and body must either both be present or both be absent"
        }
    }

    fun asExecutionValue(): ExecutionValue = MapExecutionValue(linkedMapOf(
        formatReferenceKey to TextExecutionValue(formatReference),
        formatBodyKey to formatBody,
        schemaReferenceKey to nullableText(schemaReference),
        schemaBodyKey to (schemaBody ?: NullExecutionValue),
        editorKey to (editor?.let { value -> MapExecutionValue(mapOf(
            editorReferenceKey to TextExecutionValue(value.editorReference),
            labelKey to TextExecutionValue(value.label))) } ?: NullExecutionValue),
        encodingKey to nullableText(encoding)))

    companion object {
        private const val formatReferenceKey = "formatReference"
        private const val formatBodyKey = "formatBody"
        private const val schemaReferenceKey = "schemaReference"
        private const val schemaBodyKey = "schemaBody"
        private const val editorKey = "editor"
        private const val editorReferenceKey = "editorReference"
        private const val labelKey = "label"
        private const val encodingKey = "encoding"

        fun ofExecutionValue(value: ExecutionValue): FormatMaterializationActionResult {
            val root = value as? MapExecutionValue
                ?: throw IllegalArgumentException("Format materialization result must be a map")
            val editor = root.optionalMap(editorKey)
            return FormatMaterializationActionResult(
                root.requiredText(formatReferenceKey),
                root.requiredMap(formatBodyKey),
                root.optionalText(schemaReferenceKey),
                root.optionalMap(schemaBodyKey),
                editor?.let { FormatOverrideEditorMetadata(
                    it.requiredText(editorReferenceKey),
                    it.requiredText(labelKey)) },
                root.optionalText(encodingKey))
        }

        private fun nullableText(value: String?): ExecutionValue =
            value?.let(::TextExecutionValue) ?: NullExecutionValue

        private fun MapExecutionValue.requiredMap(key: String): MapExecutionValue = values[key] as? MapExecutionValue
            ?: throw IllegalArgumentException("'$key' must be a map")

        private fun MapExecutionValue.optionalMap(key: String): MapExecutionValue? = when (val value = values[key]) {
            null, NullExecutionValue -> null
            is MapExecutionValue -> value
            else -> throw IllegalArgumentException("'$key' must be a map or null")
        }

        private fun MapExecutionValue.requiredText(key: String): String =
            (values[key] as? TextExecutionValue)?.value
                ?: throw IllegalArgumentException("'$key' must be text")

        private fun MapExecutionValue.optionalText(key: String): String? = when (val value = values[key]) {
            null, NullExecutionValue -> null
            is TextExecutionValue -> value.value
            else -> throw IllegalArgumentException("'$key' must be text or null")
        }
    }
}
