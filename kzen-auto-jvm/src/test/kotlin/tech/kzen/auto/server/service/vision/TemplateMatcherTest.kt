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
     * Calibration data for score-based matching (feature plan phase 4): best zero-mean grayscale
     * NCC over this pair measures ~0.85 at (374, 518), the true icon location and the global
     * maximum — below the 0.95 default threshold that phase pre-decided, so this fixture should
     * become its positive test and inform the default.
     */
    @Test
    fun rasterizationDriftFindsNoExactMatch() {
        val source = resourceGrid("/vision/rasterization-drift-screenshot.png")
        val crop = resourceGrid("/vision/rasterization-drift-crop.png")

        val located = TemplateMatcher.locate(source, crop)

        assertTrue(located.isEmpty(), "unexpected exact match: $located")
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
