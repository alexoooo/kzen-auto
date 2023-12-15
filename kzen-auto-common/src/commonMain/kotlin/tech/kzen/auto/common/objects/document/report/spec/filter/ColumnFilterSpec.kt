package tech.kzen.auto.common.objects.document.report.spec.filter

import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible
import tech.kzen.lib.platform.collect.persistentMapOf


data class ColumnFilterSpec(
    val type: ColumnFilterType,
    val values: Set<String>
):
    Digestible
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = ColumnFilterSpec(ColumnFilterType.RequireAny, setOf())

        val typeAttributeSegment = AttributeSegment.ofKey("type")
        val valuesAttributeSegment = AttributeSegment.ofKey("values")

        val emptyNotation = MapAttributeNotation(persistentMapOf(
            typeAttributeSegment to ScalarAttributeNotation(ColumnFilterType.RequireAny.name),
            valuesAttributeSegment to ListAttributeNotation.empty))


        fun ofNotation(attributeNotation: MapAttributeNotation): ColumnFilterSpec {
            val typeAttribute =
                attributeNotation[typeAttributeSegment] as ScalarAttributeNotation

            val type = ColumnFilterType.valueOf(typeAttribute.value)

            val valuesAttribute =
                attributeNotation[valuesAttributeSegment] as ListAttributeNotation

            val values = valuesAttribute
                .values
                .map { it as ScalarAttributeNotation }
                .map { it.value }
                .toSet()

            return ColumnFilterSpec(type, values)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun isEmpty(): Boolean {
        return values.isEmpty()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(sink: Digest.Sink) {
        sink.addInt(type.ordinal)
        sink.addUnorderedCollection(values) { addUtf8(it) }
    }
}