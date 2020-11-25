package tech.kzen.auto.common.objects.document.report.spec

import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible
import tech.kzen.lib.platform.collect.persistentMapOf


data class ColumnFilterSpec(
    val type: ColumnFilterType,
    val values: Set<String>
):
    Digestible
{
    //-----------------------------------------------------------------------------------------------------------------
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


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(builder: Digest.Builder) {
        builder.addInt(type.ordinal)
        builder.addDigestibleUnorderedList(values.map { Digest.ofUtf8(it) })
    }
}