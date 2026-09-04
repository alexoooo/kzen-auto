package tech.kzen.auto.common.data.schema

import tech.kzen.auto.common.objects.document.data.schema.DataSchemaConventions
import tech.kzen.auto.common.objects.document.data.schema.DataSchemaFieldListSpec
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.platform.collect.toPersistentMap


object AuthoredRecordSchemaNotation {
    const val prototypeReference = "auto-common/common-document.yaml#AuthoredRecordSchema"

    fun body(contract: DataContract): MapAttributeNotation? =
        AuthoredRecordSchemaDraft.from(contract)?.let(::body)

    fun body(draft: DataSchemaFieldListSpec): MapAttributeNotation = MapAttributeNotation(mapOf(
        NotationConventions.isAttributeSegment to ScalarAttributeNotation(prototypeReference),
        AttributeSegment.ofKey(DataSchemaConventions.fieldsAttributeName.value) to fields(draft)
    ).toPersistentMap())

    fun fields(draft: DataSchemaFieldListSpec): MapAttributeNotation = MapAttributeNotation(
        draft.fields.map { (fieldName, fieldSpec) ->
            AttributeSegment.ofKey(fieldName) to fieldSpec.asNotation()
        }.toMap().toPersistentMap())
}
