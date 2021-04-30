package tech.kzen.auto.common.objects.document.report.spec.input

import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.platform.ClassName


data class InputSelectionSpec(
    val dataType: ClassName,
    val locations: List<InputDataSpec>
) {
    companion object {
        private val dataTypeKey = AttributeSegment.ofKey("dataType")
        val dataTypeAttributePath = InputSpec.selectionAttributePath.nest(dataTypeKey)

        private val locationsKey = AttributeSegment.ofKey("locations")
        val locationsAttributePath = InputSpec.selectionAttributePath.nest(locationsKey)


        fun locationNesting(index: Int): AttributeNesting {
            return locationsAttributePath.nest(AttributeSegment.ofIndex(index)).nesting
        }


        fun ofNotation(mapAttributeNotation: MapAttributeNotation): InputSelectionSpec {
            val dataType = ClassName(mapAttributeNotation.values[dataTypeKey]!!.asString()!!)

            val locationsAttributeNotation = mapAttributeNotation.values[locationsKey] as ListAttributeNotation
            val locations = locationsAttributeNotation
                .values
                .map { InputDataSpec.ofNotation(it as MapAttributeNotation) }

            return InputSelectionSpec(
                dataType, locations)
        }
    }
}