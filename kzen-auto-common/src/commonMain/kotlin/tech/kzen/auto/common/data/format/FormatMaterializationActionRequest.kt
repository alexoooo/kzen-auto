package tech.kzen.auto.common.data.format

import kotlinx.serialization.Serializable
import tech.kzen.auto.common.data.model.DataPart


@Serializable
data class FormatMaterializationActionRequest(
    val part: DataPart,
    val concreteFormatReference: String,
    val overrides: Map<String, String?>,
    val intent: FormatMaterializationIntent = FormatMaterializationIntent.Override
) {
    init {
        require(concreteFormatReference.isNotBlank()) { "Concrete format reference must not be blank" }
        require(overrides.keys.none(String::isBlank)) { "Format-override names must not be blank" }
    }
}
