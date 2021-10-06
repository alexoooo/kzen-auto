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
            val previewStart = readLong(previewStartKey, notation)
                ?: 1

            val previewCount = readLong(previewRowCountKey, notation)?.toInt()
                ?: 0

            return OutputExploreSpec(previewStart, previewCount)
        }


        private fun readLong(key: String, notation: MapAttributeNotation): Long? {
            val value = notation.get(key)?.asString()
                ?: return null
//            println("$$ readLong - $value")

            val dotIndex = value.indexOf(".")

            val wholePart =
                if (dotIndex != -1) {
                    value.substring(0 until dotIndex)
                }
                else {
                    value
                }

            val numberText = wholePart.filter { it.isDigit() }

//            println("$$ readLong --- $numberText - ${numberText.toLongOrNull()}")
            return numberText.toLongOrNull()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun previewStartZeroBased(): Long {
        return previewStart - 1
    }
}