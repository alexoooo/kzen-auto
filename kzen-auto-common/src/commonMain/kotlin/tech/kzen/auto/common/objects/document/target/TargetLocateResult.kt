package tech.kzen.auto.common.objects.document.target

import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.ListExecutionValue
import tech.kzen.lib.common.exec.LongExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.model.structure.resource.ResourcePath


/**
 * Wire model for TargetLocateAction: every crop's matches in a screenshot, plus the screenshot's
 * dimensions (so the client can express the match rectangles as percentages of the displayed
 * image without decoding the pixels itself).
 */
data class TargetLocateResult(
    val screenshotWidth: Int,
    val screenshotHeight: Int,
    val matchesByCrop: Map<ResourcePath, List<TargetMatchRect>>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val widthKey = "width"
        private const val heightKey = "height"
        private const val matchesKey = "matches"


        fun ofCollection(collection: Map<String, Any>): TargetLocateResult {
            @Suppress("UNCHECKED_CAST")
            val matches = collection[matchesKey] as Map<String, List<Map<String, Any>>>

            return TargetLocateResult(
                (collection[widthKey] as Long).toInt(),
                (collection[heightKey] as Long).toInt(),
                matches
                    .map { (resourcePath, rectangles) ->
                        ResourcePath.parse(resourcePath) to
                                rectangles.map { TargetMatchRect.ofCollection(it) }
                    }
                    .toMap())
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun asExecutionValue(): ExecutionValue {
        return MapExecutionValue(mapOf(
            widthKey to LongExecutionValue(screenshotWidth.toLong()),
            heightKey to LongExecutionValue(screenshotHeight.toLong()),
            matchesKey to MapExecutionValue(matchesByCrop.entries.associate { (resourcePath, matches) ->
                resourcePath.asString() to
                        ListExecutionValue(matches.map { it.asExecutionValue() })
            })))
    }
}


data class TargetMatchRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val xKey = "x"
        private const val yKey = "y"
        private const val widthKey = "width"
        private const val heightKey = "height"


        fun ofCollection(collection: Map<String, Any>): TargetMatchRect {
            return TargetMatchRect(
                (collection[xKey] as Long).toInt(),
                (collection[yKey] as Long).toInt(),
                (collection[widthKey] as Long).toInt(),
                (collection[heightKey] as Long).toInt())
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun asExecutionValue(): ExecutionValue {
        return MapExecutionValue(mapOf(
            xKey to LongExecutionValue(x.toLong()),
            yKey to LongExecutionValue(y.toLong()),
            widthKey to LongExecutionValue(width.toLong()),
            heightKey to LongExecutionValue(height.toLong())))
    }
}
