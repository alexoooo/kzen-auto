package tech.kzen.auto.common.objects.document.process

import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.platform.collect.persistentMapOf


data class ColumnFilterSpec(
    val type: ColumnFilterType,
    val values: Set<String>
) {
    companion object {
        val typeAttributeSegment = AttributeSegment.ofKey("type")
        val valuesAttributeSegment = AttributeSegment.ofKey("values")

        val emptyNotation = MapAttributeNotation(persistentMapOf(
            typeAttributeSegment to ScalarAttributeNotation(ColumnFilterType.RequireAny.name),
            valuesAttributeSegment to ListAttributeNotation.empty))


        fun ofNotation(attributeNotation: MapAttributeNotation): ColumnFilterSpec {
            val typeAttribute =
                attributeNotation.get(typeAttributeSegment) as ScalarAttributeNotation

            val type = ColumnFilterType.valueOf(typeAttribute.value)

            val valuesAttribute =
                attributeNotation.get(valuesAttributeSegment) as ListAttributeNotation

            val values = valuesAttribute
                .values
                .map { it as ScalarAttributeNotation }
                .map { it.value }
                .toSet()

            return ColumnFilterSpec(type, values)
        }
    }
}