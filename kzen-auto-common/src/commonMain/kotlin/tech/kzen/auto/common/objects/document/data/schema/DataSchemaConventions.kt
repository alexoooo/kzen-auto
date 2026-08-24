package tech.kzen.auto.common.objects.document.data.schema

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.service.notation.NotationConventions


object DataSchemaConventions {
    val objectName = ObjectName("DataSchema")

    val fieldsAttributeName = AttributeName("fields")
    val fieldsAttributePath = AttributePath.ofName(fieldsAttributeName)


    fun isDataSchema(documentNotation: DocumentNotation): Boolean {
        val mainObjectNotation =
            documentNotation.objects.notations[NotationConventions.mainObjectPath]
            ?: return false

        val mainObjectIs =
            mainObjectNotation.get(NotationConventions.isAttributeName)?.asString()
            ?: return false

        return mainObjectIs == objectName.value
    }


    fun fieldListSpec(documentNotation: DocumentNotation): DataSchemaFieldListSpec? {
        val mainObjectNotation =
            documentNotation.objects.notations[NotationConventions.mainObjectPath]
            ?: return null

        val untypedFieldsAttributeNotation = mainObjectNotation.get(fieldsAttributeName)
            ?: MapAttributeNotation.empty

        val fieldsAttributeNotation = untypedFieldsAttributeNotation as? MapAttributeNotation
            ?: return null

        return DataSchemaFieldListSpec.ofAttributeNotation(fieldsAttributeNotation)
    }
}
