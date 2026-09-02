package tech.kzen.auto.common.data.read

import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.ListExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.NullExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.data.type.DataContract


data class DelimitedReadConfig(
    val framing: RecordFramingSpec,
    val dialect: DelimitedDialectSpec,
    val header: HeaderReadSpec,
    val characters: CharacterDecodingSpec,
    val schema: DataContract?,
    val typedDecode: TypedDecodePolicy
): ReaderConfig {
    fun asExecutionValue(): MapExecutionValue {
        return MapExecutionValue(mapOf(
            "framing" to MapExecutionValue(mapOf(
                "separator" to TextExecutionValue(framing.separator))),
            "dialect" to MapExecutionValue(mapOf(
                "delimiter" to TextExecutionValue(dialect.delimiter),
                "quote" to nullableText(dialect.quote),
                "escape" to TextExecutionValue(dialect.escape),
                "emptyField" to TextExecutionValue(dialect.emptyField),
                "trimming" to TextExecutionValue(dialect.trimming))),
            "header" to MapExecutionValue(mapOf(
                "policy" to TextExecutionValue(header.policy),
                "mapping" to TextExecutionValue(header.mapping))),
            "characters" to MapExecutionValue(mapOf(
                "charset" to TextExecutionValue(characters.charset),
                "bom" to TextExecutionValue(characters.bom),
                "malformed" to TextExecutionValue(characters.malformed),
                "unmappable" to TextExecutionValue(characters.unmappable))),
            "schema" to (schema?.asExecutionValue() ?: NullExecutionValue),
            "typedDecode" to MapExecutionValue(mapOf(
                "nullToken" to nullableText(typedDecode.nullToken),
                "malformedValue" to TextExecutionValue(typedDecode.malformedValue),
                "fieldOverrides" to ListExecutionValue(typedDecode.fieldOverrides.map { fieldOverride ->
                    MapExecutionValue(mapOf(
                        "path" to ListExecutionValue(fieldOverride.path.map(::TextExecutionValue)),
                        "nullToken" to nullableText(fieldOverride.nullToken)))
                })))
        ))
    }

    private fun nullableText(value: String?): ExecutionValue =
        value?.let(::TextExecutionValue) ?: NullExecutionValue
}
