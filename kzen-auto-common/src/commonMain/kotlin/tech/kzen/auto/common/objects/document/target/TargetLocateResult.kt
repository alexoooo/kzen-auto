package tech.kzen.auto.common.objects.document.target

import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.ListExecutionValue
import tech.kzen.lib.common.exec.LongExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.NumberExecutionValue
import tech.kzen.lib.common.model.structure.resource.ResourcePath


/**
 * Wire model for TargetLocateAction: every crop's matches in a screenshot, plus the screenshot's
 * dimensions (so the client can express the match rectangles as percentages of the displayed
 * image without decoding the pixels itself).
 */
data class TargetLocateResult(
    val screenshotWidth: Int,
    val screenshotHeight: Int,
    val matchesByCrop: Map<ResourcePath, TargetCropMatches>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val widthKey = "width"
        private const val heightKey = "height"
        private const val matchesKey = "matches"


        fun ofCollection(collection: Map<String, Any>): TargetLocateResult {
            @Suppress("UNCHECKED_CAST")
            val matches = collection[matchesKey] as Map<String, Map<String, Any?>>

            return TargetLocateResult(
                (collection[widthKey] as Long).toInt(),
                (collection[heightKey] as Long).toInt(),
                matches
                    .map { (resourcePath, cropMatches) ->
                        ResourcePath.parse(resourcePath) to
                                TargetCropMatches.ofCollection(cropMatches)
                    }
                    .toMap())
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun asExecutionValue(): ExecutionValue {
        return MapExecutionValue(mapOf(
            widthKey to LongExecutionValue(screenshotWidth.toLong()),
            heightKey to LongExecutionValue(screenshotHeight.toLong()),
            matchesKey to MapExecutionValue(matchesByCrop.entries.associate { (resourcePath, cropMatches) ->
                resourcePath.asString() to cropMatches.asExecutionValue()
            })))
    }
}


/**
 * One crop's result: the matches, plus (only when there are none and tolerant matching ran)
 * the closest-scoring candidate — the "how close was it" diagnostic that guides tolerance tuning.
 */
data class TargetCropMatches(
    val matches: List<TargetMatchRect>,
    val closest: TargetMatchRect?
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val matchesKey = "matches"
        private const val closestKey = "closest"


        fun ofCollection(collection: Map<String, Any?>): TargetCropMatches {
            @Suppress("UNCHECKED_CAST")
            val matches = collection[matchesKey] as List<Map<String, Any>>

            @Suppress("UNCHECKED_CAST")
            val closest = collection[closestKey] as Map<String, Any>?

            return TargetCropMatches(
                matches.map { TargetMatchRect.ofCollection(it) },
                closest?.let { TargetMatchRect.ofCollection(it) })
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun asExecutionValue(): ExecutionValue {
        return MapExecutionValue(mapOf(
            matchesKey to ListExecutionValue(matches.map { it.asExecutionValue() }),
            closestKey to (closest?.asExecutionValue() ?: ExecutionValue.of(null))))
    }
}


/**
 * A located window: position plus the match's score (1.0 = pixel-exact) and the crop rescale
 * factor it was found at (1.0 = the crop's own size; other values indicate monitor-scale drift).
 */
data class TargetMatchRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val score: Double,
    val scale: Double
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val xKey = "x"
        private const val yKey = "y"
        private const val widthKey = "width"
        private const val heightKey = "height"
        private const val scoreKey = "score"
        private const val scaleKey = "scale"


        fun ofCollection(collection: Map<String, Any>): TargetMatchRect {
            return TargetMatchRect(
                (collection[xKey] as Long).toInt(),
                (collection[yKey] as Long).toInt(),
                (collection[widthKey] as Long).toInt(),
                (collection[heightKey] as Long).toInt(),
                (collection[scoreKey] as Number).toDouble(),
                (collection[scaleKey] as Number).toDouble())
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun asExecutionValue(): ExecutionValue {
        return MapExecutionValue(mapOf(
            xKey to LongExecutionValue(x.toLong()),
            yKey to LongExecutionValue(y.toLong()),
            widthKey to LongExecutionValue(width.toLong()),
            heightKey to LongExecutionValue(height.toLong()),
            scoreKey to NumberExecutionValue(score),
            scaleKey to NumberExecutionValue(scale)))
    }
}
