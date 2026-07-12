package tech.kzen.auto.server.service.vision

import java.awt.Rectangle
import kotlin.math.roundToInt
import kotlin.math.sqrt


/**
 * Template matching over [RgbGrid]s: exact (pixel-equality) via [locate], and score-based
 * (zero-mean grayscale normalized cross-correlation, with a multi-scale pyramid for monitor-scale
 * drift) via [locateScored].
 */
object TemplateMatcher {
    //-----------------------------------------------------------------------------------------------------------------
    // 4 bits per RGB channel
    private const val quantizedColorBuckets = 4096

    // Windows with less luminance variation than this can't hold a structured template
    // (and would make the NCC denominator degenerate)
    private const val flatVarianceEpsilon = 1e-3

    // Common monitor-scale (DPR) drift ratios, nearest-to-1 first: the crop is rescaled by each
    // and scanning stops at the first scale with a match — a target renders at ONE scale, so
    // further scales would only add false positives (and wall-clock)
    private val matchScales = doubleArrayOf(1.0, 1.1, 0.9, 1.25, 0.8, 0.75, 1.5, 0.67)

    // A tolerant scan reports at most this many matches (the click path only needs to prove
    // non-uniqueness; the preview overlay doesn't benefit from more)
    private const val maxScoredMatches = 64


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * Colour frequencies of [grid] quantized to [quantizedColorBuckets], for probe-pixel
     * selection in [locate]. Callers matching several targets against one source can compute
     * this once and pass it in.
     */
    fun quantizedColorHistogram(grid: RgbGrid): IntArray {
        val histogram = IntArray(quantizedColorBuckets)
        for (y in 0 until grid.height) {
            for (x in 0 until grid.width) {
                histogram[quantize(grid.get(x, y))]++
            }
        }
        return histogram
    }


    private fun quantize(rgb: Int): Int {
        return ((rgb ushr 12) and 0xF00) or
                ((rgb ushr 8) and 0xF0) or
                ((rgb ushr 4) and 0xF)
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * Origins of every window of [source] that equals [target] pixel-for-pixel, up to [limit]
     * (callers that only need to prove non-uniqueness pass 2).
     *
     * Each candidate origin is first screened by a probe pixel — the target pixel whose colour
     * bucket is rarest in [source] — so a crop sharing its background colour with most of the
     * source doesn't degenerate toward a full window compare per origin. A crop consisting
     * entirely of common colours still degenerates; acceptable.
     */
    fun locate(
        source: RgbGrid,
        target: RgbGrid,
        limit: Int = Int.MAX_VALUE,
        sourceHistogram: IntArray = quantizedColorHistogram(source)
    ): List<Rectangle> {
        val matches = mutableListOf<Rectangle>()

        val maxOffsetX = source.width - target.width
        val maxOffsetY = source.height - target.height
        if (maxOffsetX < 0 || maxOffsetY < 0) {
            return matches
        }

        var probeX = 0
        var probeY = 0
        var probeFrequency = Int.MAX_VALUE
        for (y in 0 until target.height) {
            for (x in 0 until target.width) {
                val frequency = sourceHistogram[quantize(target.get(x, y))]
                if (frequency < probeFrequency) {
                    probeFrequency = frequency
                    probeX = x
                    probeY = y
                }
            }
        }
        val probeRgb = target.get(probeX, probeY)

        for (offsetY in 0..maxOffsetY) {
            for (offsetX in 0..maxOffsetX) {
                if (source.get(offsetX + probeX, offsetY + probeY) != probeRgb) {
                    continue
                }

                if (matches(source, target, offsetX, offsetY)) {
                    matches.add(Rectangle(
                        offsetX, offsetY, target.width, target.height))

                    if (matches.size >= limit) {
                        return matches
                    }
                }
            }
        }

        return matches
    }


    /**
     * Caller guarantees the window is in bounds:
     * offsetX + target.width <= source.width and offsetY + target.height <= source.height.
     */
    fun matches(
        source: RgbGrid,
        target: RgbGrid,
        offsetX: Int,
        offsetY: Int
    ): Boolean {
        for (y in 0 until target.height) {
            for (x in 0 until target.width) {
                val sourceRgb = source.get(offsetX + x, offsetY + y)
                val targetRgb = target.get(x, y)

                if (sourceRgb != targetRgb) {
                    return false
                }
            }
        }

        return true
    }


    //-----------------------------------------------------------------------------------------------------------------
    /** A score-based match: NCC score in [-1, 1] (1 = pixel-identical up to brightness/contrast),
     *  and the crop rescale factor it was found at (1.0 = the crop's own size). */
    data class ScoredMatch(
        val rect: Rectangle,
        val score: Double,
        val scale: Double
    )


    /** [matches] pass [threshold]; [best] is the highest-scoring candidate overall (even below
     *  threshold — the "how close was it" diagnostic when [matches] is empty). */
    data class ScoredResult(
        val matches: List<ScoredMatch>,
        val best: ScoredMatch?
    ) {
        companion object {
            val empty = ScoredResult(listOf(), null)
        }
    }


    /**
     * Grayscale plane of a source image with integral images (sum and sum of squares), so a
     * scored scan gets each window's mean/variance in O(1). Callers matching several crops
     * against one source can compute this once and pass it in.
     */
    class SourceLuminance(grid: RgbGrid) {
        val width = grid.width
        val height = grid.height

        val luminance = IntArray(width * height)

        // (width + 1) x (height + 1), row-major, first row/column zero
        private val integralSum = LongArray((width + 1) * (height + 1))
        private val integralSumSq = LongArray((width + 1) * (height + 1))

        init {
            for (y in 0 until height) {
                var rowSum = 0L
                var rowSumSq = 0L
                for (x in 0 until width) {
                    val lum = luminanceOf(grid.get(x, y))
                    luminance[y * width + x] = lum

                    rowSum += lum
                    rowSumSq += lum.toLong() * lum

                    val i = (y + 1) * (width + 1) + (x + 1)
                    integralSum[i] = integralSum[i - (width + 1)] + rowSum
                    integralSumSq[i] = integralSumSq[i - (width + 1)] + rowSumSq
                }
            }
        }

        fun windowSum(x: Int, y: Int, w: Int, h: Int): Long {
            return corners(integralSum, x, y, w, h)
        }

        fun windowSumSq(x: Int, y: Int, w: Int, h: Int): Long {
            return corners(integralSumSq, x, y, w, h)
        }

        private fun corners(integral: LongArray, x: Int, y: Int, w: Int, h: Int): Long {
            val stride = width + 1
            return integral[(y + h) * stride + (x + w)] -
                    integral[(y + h) * stride + x] -
                    integral[y * stride + (x + w)] +
                    integral[y * stride + x]
        }
    }


    /**
     * Score-based multi-scale matching: every window of [source] whose zero-mean grayscale NCC
     * against [target] (rescaled through [matchScales]) reaches [threshold], collapsed to local
     * maxima (non-max suppression within a crop-sized neighbourhood), strongest first, at most
     * [maxScoredMatches]. Insensitive to uniform brightness/contrast shifts by construction;
     * a featureless (single-colour) crop has no structure to correlate and finds nothing.
     */
    fun locateScored(
        source: RgbGrid,
        target: RgbGrid,
        threshold: Double,
        sourceLuminance: SourceLuminance = SourceLuminance(source)
    ): ScoredResult {
        var bestAtOwnScale: ScoredMatch? = null
        var bestGlobal: ScoredMatch? = null
        val candidates = mutableListOf<ScoredMatch>()

        for (scale in matchScales) {
            val scaled =
                if (scale == 1.0) { target }
                else { rescale(target, scale) }

            if (scaled.width > source.width || scaled.height > source.height ||
                    scaled.width < 2 || scaled.height < 2) {
                continue
            }

            val scaleBest = scanScale(
                sourceLuminance, scaled, scale, threshold, candidates)

            if (scaleBest != null) {
                if (scale == 1.0) {
                    bestAtOwnScale = scaleBest
                }
                if (bestGlobal == null || scaleBest.score > bestGlobal.score) {
                    bestGlobal = scaleBest
                }
            }

            // The target renders at ONE scale, and scales are ordered nearest-to-1 first (the
            // a-priori likeliest): once a scale matches, further scales would only add false
            // positives — NCC scores aren't comparable across window sizes (smaller windows
            // score higher on less evidence), so "keep scanning and take the best" would let a
            // small-scale false positive outrank a true match.
            if (candidates.isNotEmpty()) {
                break
            }
        }

        // The "how close was it" diagnostic reports the crop at its OWN scale where possible —
        // a cross-scale global best suffers the same small-window inflation as above.
        return ScoredResult(nonMaxSuppress(candidates), bestAtOwnScale ?: bestGlobal)
    }


    /** Best-effort scan of one crop scale: candidates >= threshold into [candidates],
     *  returns the scale's best origin regardless of threshold. */
    private fun scanScale(
        source: SourceLuminance,
        scaledTarget: RgbGrid,
        scale: Double,
        threshold: Double,
        candidates: MutableList<ScoredMatch>
    ): ScoredMatch? {
        val tw = scaledTarget.width
        val th = scaledTarget.height
        val n = tw * th

        // Zero-mean template and its norm
        val templateLum = DoubleArray(n)
        var templateSum = 0.0
        for (y in 0 until th) {
            for (x in 0 until tw) {
                val lum = luminanceOf(scaledTarget.get(x, y)).toDouble()
                templateLum[y * tw + x] = lum
                templateSum += lum
            }
        }
        val templateMean = templateSum / n
        var templateNormSq = 0.0
        for (i in 0 until n) {
            templateLum[i] -= templateMean
            templateNormSq += templateLum[i] * templateLum[i]
        }
        if (templateNormSq < flatVarianceEpsilon) {
            // Featureless template: NCC is undefined (0/0)
            return null
        }
        val templateNorm = sqrt(templateNormSq)

        val sourceLum = source.luminance
        val sw = source.width

        var best: ScoredMatch? = null

        for (offsetY in 0..source.height - th) {
            for (offsetX in 0..sw - tw) {
                val windowSum = source.windowSum(offsetX, offsetY, tw, th)
                val windowSumSq = source.windowSumSq(offsetX, offsetY, tw, th)
                val windowVarSum = windowSumSq - windowSum.toDouble() * windowSum / n
                if (windowVarSum < flatVarianceEpsilon) {
                    // Flat window: can't hold a structured template
                    continue
                }

                // Numerator: correlation of the raw window with the zero-mean template
                // (equals the zero-mean/zero-mean correlation since the template sums to 0)
                var numerator = 0.0
                for (ty in 0 until th) {
                    var sourceIndex = (offsetY + ty) * sw + offsetX
                    var templateIndex = ty * tw
                    for (tx in 0 until tw) {
                        numerator += sourceLum[sourceIndex] * templateLum[templateIndex]
                        sourceIndex++
                        templateIndex++
                    }
                }

                val score = numerator / (templateNorm * sqrt(windowVarSum))

                if (score >= threshold) {
                    candidates.add(ScoredMatch(
                        Rectangle(offsetX, offsetY, tw, th), score, scale))
                }
                if (best == null || score > best.score) {
                    best = ScoredMatch(
                        Rectangle(offsetX, offsetY, tw, th), score, scale)
                }
            }
        }

        return best
    }


    /** Collapse adjacent candidates (the score surface is smooth around a true match) to local
     *  maxima: strongest first, suppressing anything within half a crop of a kept match. */
    private fun nonMaxSuppress(candidates: List<ScoredMatch>): List<ScoredMatch> {
        if (candidates.size <= 1) {
            return candidates
        }

        val byScore = candidates.sortedByDescending { it.score }
        val kept = mutableListOf<ScoredMatch>()

        for (candidate in byScore) {
            val suppressed = kept.any {
                val dx = candidate.rect.x - it.rect.x
                val dy = candidate.rect.y - it.rect.y
                dx > -it.rect.width / 2 && dx < it.rect.width / 2 &&
                        dy > -it.rect.height / 2 && dy < it.rect.height / 2
            }
            if (!suppressed) {
                kept.add(candidate)
                if (kept.size >= maxScoredMatches) {
                    break
                }
            }
        }

        return kept
    }


    //-----------------------------------------------------------------------------------------------------------------
    // ITU-R BT.601 luma, integer-scaled
    private fun luminanceOf(rgb: Int): Int {
        val r = (rgb ushr 16) and 0xFF
        val g = (rgb ushr 8) and 0xFF
        val b = rgb and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }


    /** Bilinear rescale by [scale] (both axes), for the monitor-scale pyramid. */
    fun rescale(grid: RgbGrid, scale: Double): RgbGrid {
        val width = (grid.width * scale).roundToInt().coerceAtLeast(1)
        val height = (grid.height * scale).roundToInt().coerceAtLeast(1)

        val values = IntArray(width * height)

        val xRatio = grid.width.toDouble() / width
        val yRatio = grid.height.toDouble() / height

        for (y in 0 until height) {
            val sourceY = ((y + 0.5) * yRatio - 0.5).coerceIn(0.0, grid.height - 1.0)
            val y0 = sourceY.toInt().coerceAtMost(grid.height - 1)
            val y1 = (y0 + 1).coerceAtMost(grid.height - 1)
            val yFraction = sourceY - y0

            for (x in 0 until width) {
                val sourceX = ((x + 0.5) * xRatio - 0.5).coerceIn(0.0, grid.width - 1.0)
                val x0 = sourceX.toInt().coerceAtMost(grid.width - 1)
                val x1 = (x0 + 1).coerceAtMost(grid.width - 1)
                val xFraction = sourceX - x0

                var rgb = 0
                for (shift in intArrayOf(16, 8, 0)) {
                    val c00 = (grid.get(x0, y0) ushr shift) and 0xFF
                    val c10 = (grid.get(x1, y0) ushr shift) and 0xFF
                    val c01 = (grid.get(x0, y1) ushr shift) and 0xFF
                    val c11 = (grid.get(x1, y1) ushr shift) and 0xFF

                    val top = c00 + (c10 - c00) * xFraction
                    val bottom = c01 + (c11 - c01) * xFraction
                    val value = (top + (bottom - top) * yFraction).roundToInt().coerceIn(0, 255)

                    rgb = rgb or (value shl shift)
                }

                values[y * width + x] = rgb
            }
        }

        return RgbGrid(width, height, values)
    }
}
