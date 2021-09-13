package tech.kzen.auto.common.objects.document.report.spec.output

import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation


data class OutputExploreSpec(
    val previewStart: Long,
    val previewCount: Int
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        const val previewStartKey = "start"
        val previewStartPath = OutputSpec.exploreAttributePath.nest(AttributeSegment.ofKey(previewStartKey))

        const val previewRowCountKey = "count"
        val previewCountPath = OutputSpec.exploreAttributePath.nest(AttributeSegment.ofKey(previewRowCountKey))


        fun ofNotation(notation: MapAttributeNotation): OutputExploreSpec {
            val previewStart = notation
                .get(previewStartKey)
                ?.asString()
                ?.replace(",", "")
                ?.toLongOrNull()
                ?: 1

            val previewCount = notation
                .get(previewRowCountKey)
                ?.asString()
                ?.replace(",", "")
                ?.toIntOrNull()
                ?: 0

            return OutputExploreSpec(previewStart, previewCount)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun previewStartZeroBased(): Long {
        return previewStart - 1
    }
}