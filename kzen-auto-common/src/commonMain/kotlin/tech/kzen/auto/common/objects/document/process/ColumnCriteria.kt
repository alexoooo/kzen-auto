package tech.kzen.auto.common.objects.document.process

import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.platform.collect.persistentMapOf


data class ColumnCriteria(
    val type: ColumnCriteriaType,
    val values: Set<String>
) {
    companion object {
        val typeAttributeSegment = AttributeSegment.ofKey("type")
        val valuesAttributeSegment = AttributeSegment.ofKey("values")

        val emptyNotation = MapAttributeNotation(persistentMapOf(
            typeAttributeSegment to ScalarAttributeNotation(ColumnCriteriaType.RequireAny.name),
            valuesAttributeSegment to ListAttributeNotation.empty))


        fun ofNotation(attributeNotation: MapAttributeNotation): ColumnCriteria {
            val typeAttribute =
                attributeNotation.get(typeAttributeSegment) as ScalarAttributeNotation

            val type = ColumnCriteriaType.valueOf(typeAttribute.value)

            val valuesAttribute =
                attributeNotation.get(valuesAttributeSegment) as ListAttributeNotation

            val values = valuesAttribute
                .values
                .map { it as ScalarAttributeNotation }
                .map { it.value }
                .toSet()

            return ColumnCriteria(type, values)
        }
    }
}