package tech.kzen.auto.common.data.read

import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue


data class PlainTextReadConfig(
    val characters: CharacterDecodingSpec
): ReaderConfig {
    fun asExecutionValue(): MapExecutionValue = MapExecutionValue(mapOf(
        "characters" to MapExecutionValue(mapOf(
            "charset" to TextExecutionValue(characters.charset),
            "bom" to TextExecutionValue(characters.bom),
            "malformed" to TextExecutionValue(characters.malformed),
            "unmappable" to TextExecutionValue(characters.unmappable)))))
}
