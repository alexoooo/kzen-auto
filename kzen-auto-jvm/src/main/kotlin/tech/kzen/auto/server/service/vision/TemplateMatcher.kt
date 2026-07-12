package tech.kzen.auto.server.service.vision

import java.awt.Rectangle


/**
 * Exact (pixel-equality) template matching over [RgbGrid]s.
 */
object TemplateMatcher {
    //-----------------------------------------------------------------------------------------------------------------
    // 4 bits per RGB channel
    private const val quantizedColorBuckets = 4096


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
}
