package tech.kzen.auto.server.service.vision

import org.junit.Test
import java.awt.Rectangle
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class TemplateMatcherTest {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val white = 0xFFFFFF
        private const val red = 0xFF0000
        private const val blue = 0x0000FF

        private const val benchmarkSourceWidth = 1920
        private const val benchmarkSourceHeight = 1080
        private const val benchmarkCropSize = 32
        private const val locateBenchmarkBudgetMillis = 1_000L
        private const val scoredBenchmarkBudgetMillis = 5_000L
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun image(width: Int, height: Int, background: Int = white): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until height) {
            for (x in 0 until width) {
                image.setRGB(x, y, background)
            }
        }
        return image
    }


    private fun BufferedImage.embed(crop: BufferedImage, originX: Int, originY: Int): BufferedImage {
        for (y in 0 until crop.height) {
            for (x in 0 until crop.width) {
                setRGB(originX + x, originY + y, crop.getRGB(x, y))
            }
        }
        return this
    }


    /**
     * Scan order is an implementation detail: assert membership, not ordering.
     */
    private fun assertLocated(expected: Set<Rectangle>, actual: List<Rectangle>) {
        assertEquals(expected.size, actual.size, "match count: $actual")
        assertEquals(expected, actual.toSet())
    }


    private fun crossCrop(size: Int): BufferedImage {
        val crop = image(size, size, red)
        for (i in 0 until size) {
            crop.setRGB(i, size / 2, blue)
            crop.setRGB(size / 2, i, blue)
        }
        return crop
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun singleMatchInInterior() {
        val crop = crossCrop(3)
        val source = image(20, 15).embed(crop, 5, 4)

        assertLocated(
            setOf(Rectangle(5, 4, 3, 3)),
            TemplateMatcher.locate(RgbGrid.ofImage(source), RgbGrid.ofImage(crop)))
    }


    @Test
    fun noMatchWhenOnePixelDiffers() {
        val crop = crossCrop(3)
        val almostCrop = crossCrop(3)
        almostCrop.setRGB(1, 1, white)
        val source = image(20, 15).embed(almostCrop, 5, 4)

        assertLocated(
            setOf(),
            TemplateMatcher.locate(RgbGrid.ofImage(source), RgbGrid.ofImage(crop)))
    }


    @Test
    fun multipleMatchesAllReported() {
        val crop = crossCrop(3)
        val source = image(20, 15)
            .embed(crop, 1, 1)
            .embed(crop, 10, 3)
            .embed(crop, 4, 11)

        assertLocated(
            setOf(
                Rectangle(1, 1, 3, 3),
                Rectangle(10, 3, 3, 3),
                Rectangle(4, 11, 3, 3)),
            TemplateMatcher.locate(RgbGrid.ofImage(source), RgbGrid.ofImage(crop)))
    }


    @Test
    fun matchAtEachCorner() {
        val crop = crossCrop(3)
        val source = image(12, 10)
            .embed(crop, 0, 0)
            .embed(crop, 9, 0)
            .embed(crop, 0, 7)
            .embed(crop, 9, 7)

        assertLocated(
            setOf(
                Rectangle(0, 0, 3, 3),
                Rectangle(9, 0, 3, 3),
                Rectangle(0, 7, 3, 3),
                Rectangle(9, 7, 3, 3)),
            TemplateMatcher.locate(RgbGrid.ofImage(source), RgbGrid.ofImage(crop)))
    }


    @Test
    fun matchFlushToRightEdge() {
        val crop = crossCrop(3)
        val source = image(12, 10).embed(crop, 9, 4)

        assertLocated(
            setOf(Rectangle(9, 4, 3, 3)),
            TemplateMatcher.locate(RgbGrid.ofImage(source), RgbGrid.ofImage(crop)))
    }


    @Test
    fun matchFlushToBottomEdge() {
        val crop = crossCrop(3)
        val source = image(12, 10).embed(crop, 4, 7)

        assertLocated(
            setOf(Rectangle(4, 7, 3, 3)),
            TemplateMatcher.locate(RgbGrid.ofImage(source), RgbGrid.ofImage(crop)))
    }


    @Test
    fun singlePixelCropMatchesEveryOccurrence() {
        val crop = image(1, 1, red)
        val source = image(8, 6)
        source.setRGB(0, 0, red)
        source.setRGB(6, 2, red)
        source.setRGB(3, 5, red)

        assertLocated(
            setOf(
                Rectangle(0, 0, 1, 1),
                Rectangle(6, 2, 1, 1),
                Rectangle(3, 5, 1, 1)),
            TemplateMatcher.locate(RgbGrid.ofImage(source), RgbGrid.ofImage(crop)))
    }


    @Test
    fun cropEqualToSourceMatchesOnceAtOrigin() {
        val crop = crossCrop(5)

        assertLocated(
            setOf(Rectangle(0, 0, 5, 5)),
            TemplateMatcher.locate(RgbGrid.ofImage(crop), RgbGrid.ofImage(crop)))
    }


    @Test
    fun cropWiderThanSourceFindsNothing() {
        val crop = image(6, 2, red)
        val source = image(4, 4)

        assertLocated(
            setOf(),
            TemplateMatcher.locate(RgbGrid.ofImage(source), RgbGrid.ofImage(crop)))
    }


    @Test
    fun cropTallerThanSourceFindsNothing() {
        val crop = image(2, 6, red)
        val source = image(4, 4)

        assertLocated(
            setOf(),
            TemplateMatcher.locate(RgbGrid.ofImage(source), RgbGrid.ofImage(crop)))
    }


    @Test
    fun limitTwoStopsAfterSecondMatch() {
        val crop = crossCrop(3)
        val source = image(20, 15)
            .embed(crop, 1, 1)
            .embed(crop, 10, 3)
            .embed(crop, 4, 11)

        val located = TemplateMatcher.locate(
            RgbGrid.ofImage(source), RgbGrid.ofImage(crop), limit = 2)

        assertEquals(2, located.size, "limit exceeded: $located")
        val all = setOf(
            Rectangle(1, 1, 3, 3),
            Rectangle(10, 3, 3, 3),
            Rectangle(4, 11, 3, 3))
        assertTrue(all.containsAll(located), "unexpected matches: $located")
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * Real-world fixture: a Target crop (a `<>` code icon, cropped from a desktop
     * screenshot) against a separately captured desktop screenshot of the same UI. The icon is
     * visibly present, but rasterized differently — 19x12 vs 18x11 glyph footprint, every
     * antialiased edge shade shifted — so exact matching finds nothing. This is the technique's
     * documented boundary (capture and match must share one rendering), not a matcher defect.
     *
     * Calibration data for score-based matching (target-improvements plan phase 5): best zero-mean
     * grayscale NCC over this pair measures ~0.85 at (374, 518), the true icon location and the
     * global maximum — the fixture becomes that phase's positive test and calibrates its
     * suggested tolerance levels.
     */
    @Test
    fun rasterizationDriftFindsNoExactMatch() {
        val source = resourceGrid("/vision/rasterization-drift-screenshot.png")
        val crop = resourceGrid("/vision/rasterization-drift-crop.png")

        val located = TemplateMatcher.locate(source, crop)

        assertTrue(located.isEmpty(), "unexpected exact match: $located")
    }


    /**
     * The positive counterpart (calibration): zero-mean grayscale NCC scores the drifted icon
     * 0.850 at its true location — the global maximum. This measurement is what calibrates the
     * tolerance presets (Normal 0.8 catches same-machine rasterization drift; Strict 0.9 does
     * not) — re-run it before moving any preset.
     */
    @Test
    fun rasterizationDriftFoundAtNormalTolerance() {
        val source = resourceGrid("/vision/rasterization-drift-screenshot.png")
        val crop = resourceGrid("/vision/rasterization-drift-crop.png")

        val normal = TemplateMatcher.locateScored(source, crop, 0.8)

        assertEquals(1, normal.matches.size, "expected single match: ${normal.matches}")
        val match = normal.matches.single()
        assertEquals(Rectangle(374, 518, 21, 17), match.rect)
        assertEquals(1.0, match.scale)
        assertTrue(match.score in 0.8..0.9, "score outside calibrated range: ${match.score}")
    }


    /** Below-threshold results still report the best candidate — the "how close was it"
     *  diagnostic that guides tolerance tuning. */
    @Test
    fun rasterizationDriftRejectedAtStrictWithBestCandidateReported() {
        val source = resourceGrid("/vision/rasterization-drift-screenshot.png")
        val crop = resourceGrid("/vision/rasterization-drift-crop.png")

        val strict = TemplateMatcher.locateScored(source, crop, 0.9)

        assertTrue(strict.matches.isEmpty(), "unexpected match: ${strict.matches}")
        // The diagnostic reports the crop at its own scale — the true icon location, not a
        // cross-scale candidate (smaller windows inflate NCC on less evidence)
        val best = checkNotNull(strict.best)
        assertEquals(Rectangle(374, 518, 21, 17), best.rect)
        assertEquals(1.0, best.scale)
        assertTrue(best.score in 0.8..0.9, "score outside calibrated range: ${best.score}")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun gradientCrossCrop(size: Int): BufferedImage {
        // Gradient background with a dark cross: enough luminance structure for NCC
        val crop = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val shade = 128 + (x + y) * 96 / (2 * size)
                crop.setRGB(x, y, (shade shl 16) or (shade shl 8) or shade)
            }
        }
        for (i in 0 until size) {
            crop.setRGB(i, size / 2, 0x202020)
            crop.setRGB(size / 2, i, 0x202020)
        }
        return crop
    }


    private fun texturedCrop(size: Int): BufferedImage {
        // Deterministic per-pixel pseudo-random shades (LCG): discriminative under rescaling,
        // unlike a smooth gradient, which self-correlates across neighbouring scales
        val crop = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
        var seed = 0x12345678L
        for (y in 0 until size) {
            for (x in 0 until size) {
                seed = (seed * 6364136223846793005L + 1442695040888963407L)
                val shade = ((seed ushr 40) and 0xFF).toInt()
                crop.setRGB(x, y, (shade shl 16) or (shade shl 8) or shade)
            }
        }
        return crop
    }


    @Test
    fun noisePerturbedPatchFoundByScoreWhereExactMisses() {
        val size = 16
        val crop = gradientCrossCrop(size)

        val perturbed = gradientCrossCrop(size)
        for (i in 0 until size step 3) {
            // Shift some pixels' brightness — enough to break pixel equality, not the pattern
            val rgb = perturbed.getRGB(i, i) and 0xFFFFFF
            val shade = ((rgb and 0xFF) + 24).coerceAtMost(255)
            perturbed.setRGB(i, i, (shade shl 16) or (shade shl 8) or shade)
        }

        val source = image(200, 150).embed(perturbed, 60, 40)
        val sourceGrid = RgbGrid.ofImage(source)
        val cropGrid = RgbGrid.ofImage(crop)

        assertTrue(TemplateMatcher.locate(sourceGrid, cropGrid).isEmpty(),
            "perturbation failed to break exact match")

        val scored = TemplateMatcher.locateScored(sourceGrid, cropGrid, 0.8)
        assertEquals(1, scored.matches.size, "expected single match: ${scored.matches}")
        assertEquals(Rectangle(60, 40, size, size), scored.matches.single().rect)

        // And rejected just above its own score: the threshold is a real cutoff
        val aboveScore = TemplateMatcher.locateScored(
            sourceGrid, cropGrid, scored.matches.single().score + 0.001)
        assertTrue(aboveScore.matches.isEmpty(), "threshold not honoured: ${aboveScore.matches}")
        assertEquals(Rectangle(60, 40, size, size), checkNotNull(aboveScore.best).rect)
    }


    @Test
    fun rescaledPatchFoundThroughScalePyramid() {
        val size = 16
        val crop = texturedCrop(size)
        val cropGrid = RgbGrid.ofImage(crop)

        // The screen renders the target at 1.25x the captured crop (monitor-scale drift)
        val rendered = TemplateMatcher.rescale(cropGrid, 1.25)
        val source = image(200, 150)
        for (y in 0 until rendered.height) {
            for (x in 0 until rendered.width) {
                source.setRGB(60 + x, 40 + y, rendered.get(x, y))
            }
        }
        val sourceGrid = RgbGrid.ofImage(source)

        assertTrue(TemplateMatcher.locate(sourceGrid, cropGrid).isEmpty(),
            "rescaled rendering should not exact-match")

        val scored = TemplateMatcher.locateScored(sourceGrid, cropGrid, 0.8)
        assertEquals(1, scored.matches.size, "expected single match: ${scored.matches}")
        val match = scored.matches.single()
        assertEquals(1.25, match.scale)
        assertEquals(Rectangle(60, 40, rendered.width, rendered.height), match.rect)
    }


    @Test
    fun nonMaxSuppressionCollapsesAdjacentHits() {
        val size = 16
        val crop = gradientCrossCrop(size)
        val source = image(200, 150)
            .embed(crop, 60, 40)
            .embed(crop, 130, 90)

        // Permissive threshold: origins adjacent to each true match also score high;
        // NMS must collapse them to the two local maxima
        val scored = TemplateMatcher.locateScored(
            RgbGrid.ofImage(source), RgbGrid.ofImage(crop), 0.5)

        for ((i, a) in scored.matches.withIndex()) {
            for (b in scored.matches.drop(i + 1)) {
                assertTrue(
                    kotlin.math.abs(a.rect.x - b.rect.x) >= a.rect.width / 2 ||
                            kotlin.math.abs(a.rect.y - b.rect.y) >= a.rect.height / 2,
                    "adjacent matches not suppressed: $a vs $b")
            }
        }

        val exact = scored.matches.filter { it.score > 0.999 }.map { it.rect }.toSet()
        assertEquals(
            setOf(
                Rectangle(60, 40, size, size),
                Rectangle(130, 90, size, size)),
            exact)
    }


    /**
     * NCC-path performance canary, analogous to [commonColourBackgroundLocatesWellUnderASecond]:
     * a mostly-flat screenshot skips windows via the integral-image variance test. More generous
     * budget than the exact path — the tolerant scan only runs after exact matching found nothing.
     */
    @Test
    fun scoredLocateOnFlatBackgroundWellUnderBudget() {
        val crop = gradientCrossCrop(benchmarkCropSize)

        val originX = 600
        val originY = 400
        val source = image(benchmarkSourceWidth, benchmarkSourceHeight)
            .embed(crop, originX, originY)

        val sourceGrid = RgbGrid.ofImage(source)
        val cropGrid = RgbGrid.ofImage(crop)

        val startNanos = System.nanoTime()
        val scored = TemplateMatcher.locateScored(sourceGrid, cropGrid, 0.8)
        val elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000

        assertEquals(1, scored.matches.size, "expected single match: ${scored.matches}")
        assertEquals(
            Rectangle(originX, originY, benchmarkCropSize, benchmarkCropSize),
            scored.matches.single().rect)
        assertTrue(elapsedMillis < scoredBenchmarkBudgetMillis,
            "locateScored took ${elapsedMillis}ms (budget ${scoredBenchmarkBudgetMillis}ms)")
    }


    private fun resourceGrid(resourcePath: String): RgbGrid {
        val stream = checkNotNull(TemplateMatcherTest::class.java.getResourceAsStream(resourcePath)) {
            "Missing test resource: $resourcePath"
        }
        val image = stream.use { ImageIO.read(it) }
        return RgbGrid.ofImage(image)
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * Regression canary for matcher performance: the crop shares its background colour with almost
     * the entire screenshot (the common case for UI crops on white pages), which degenerates a
     * naive scan toward O(W*H*w). Generous bound; must stay green through any matcher change.
     */
    @Test
    fun commonColourBackgroundLocatesWellUnderASecond() {
        val crop = image(benchmarkCropSize, benchmarkCropSize, white)
        crop.setRGB(benchmarkCropSize / 2, benchmarkCropSize / 2, red)

        val originX = 600
        val originY = 400
        val source = image(benchmarkSourceWidth, benchmarkSourceHeight)
            .embed(crop, originX, originY)

        val sourceGrid = RgbGrid.ofImage(source)
        val cropGrid = RgbGrid.ofImage(crop)

        val startNanos = System.nanoTime()
        val located = TemplateMatcher.locate(sourceGrid, cropGrid)
        val elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000

        assertLocated(
            setOf(Rectangle(originX, originY, benchmarkCropSize, benchmarkCropSize)),
            located)
        assertTrue(elapsedMillis < locateBenchmarkBudgetMillis,
            "locate took ${elapsedMillis}ms (budget ${locateBenchmarkBudgetMillis}ms)")
    }
}
