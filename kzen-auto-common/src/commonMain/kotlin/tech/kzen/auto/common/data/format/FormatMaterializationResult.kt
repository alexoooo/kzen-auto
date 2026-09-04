package tech.kzen.auto.common.data.format

import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation


data class FormatMaterializationResult(
    val formatBody: MapAttributeNotation,
    val schemaBody: MapAttributeNotation?,
    val schemaReferenceAttribute: String?,
    val editor: FormatOverrideEditorMetadata?,
    val encoding: String?
) {
    init {
        require((schemaBody == null) == (schemaReferenceAttribute == null)) {
            "Schema body and format reference attribute must either both be present or both be absent"
        }
        require(schemaReferenceAttribute == null || schemaReferenceAttribute.isNotBlank()) {
            "Schema-reference attribute must not be blank"
        }
    }
}
