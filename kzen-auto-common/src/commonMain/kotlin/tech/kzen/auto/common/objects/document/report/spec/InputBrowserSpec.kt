package tech.kzen.auto.common.objects.document.report.spec

import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation


data class InputBrowserSpec(
    val directory: String,
    val filter: String
) {
    companion object {
        private val directoryKey = AttributeSegment.ofKey("directory")
        val directoryAttributePath = InputSpec.browserAttributePath.nest(directoryKey)

        private val filterKey = AttributeSegment.ofKey("filter")
        val filterAttributePath = InputSpec.browserAttributePath.nest(filterKey)


        fun ofNotation(notation: MapAttributeNotation): InputBrowserSpec {
            val directory = notation.get(directoryKey)?.asString()!!
            val filter = notation.get(filterKey)?.asString()!!
            return InputBrowserSpec(directory, filter)
        }
    }
}