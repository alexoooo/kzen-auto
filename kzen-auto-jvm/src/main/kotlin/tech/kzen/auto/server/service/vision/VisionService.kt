package tech.kzen.auto.server.service.vision

import org.openqa.selenium.By
import org.openqa.selenium.OutputType
import org.openqa.selenium.WebElement
import org.openqa.selenium.remote.RemoteWebDriver
import tech.kzen.auto.common.objects.document.feature.*
import tech.kzen.lib.common.model.location.ResourceLocation
import tech.kzen.lib.common.service.media.NotationMedia
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.platform.toInputStream
import java.awt.Rectangle
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.roundToInt


/**
 * Locates action targets in the browser under automation.
 */
class VisionService(
    private val notationMedia: NotationMedia
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val cropGridCacheMaxEntries = 64
        private const val cropGridCacheLoadFactor = 0.75f

        // Two matches suffice to prove non-uniqueness
        private const val uniqueMatchLimit = 2
    }


    //-----------------------------------------------------------------------------------------------------------------
    data class Result(
        val webElement: WebElement?,
        val error: String?
    ) {
        init {
            require(webElement == null && error != null ||
                    webElement != null && error == null)
        }

        fun isError(): Boolean {
            return error != null
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * Keyed by content digest: a re-captured crop has a new digest, so stale pixels can never
     * be served.
     */
    private val cropGridCache = object: LinkedHashMap<Digest, RgbGrid>(
        cropGridCacheMaxEntries, cropGridCacheLoadFactor, true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Digest, RgbGrid>): Boolean {
            return size > cropGridCacheMaxEntries
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun locateElement(
        target: TargetSpec,
        driver: RemoteWebDriver
    ): Result {
        val element = when (target) {
            FocusTarget ->
                driver.switchTo().activeElement()

            is TextTarget -> {
                val xpathEscaped = VisionUtils.xpathEscape(target.text)

                // https://stackoverflow.com/a/49906870/1941359
                // https://stackoverflow.com/a/3655588/1941359
                val foundContaining = driver.findElements(
                        By.xpath("//*[text()[contains(.,$xpathEscaped)]]"))

                if (foundContaining.isNotEmpty()) {
                    foundContaining[0]
                }
                else {
                    // e.g. buttons
                    driver.findElement(
                            By.xpath("//input[contains(@value,$xpathEscaped)]"))
                }
            }

            is XpathTarget ->
                driver.findElement(By.xpath(target.xpath))

            is VisualTarget -> {
                val targetLocation = locateElement(target.feature, driver)

                if (targetLocation.isError()) {
                    return targetLocation
                }

                targetLocation.webElement!!
            }
        }

        return Result(element, null)
    }


    suspend fun locateElement(
        target: FeatureDocument,
        driver: RemoteWebDriver
    ): Result {
        val screenshotPngBytes = driver.getScreenshotAs(OutputType.BYTES)
        val screenshotImage = ImageIO.read(ByteArrayInputStream(screenshotPngBytes))
        val screenshotGrid = RgbGrid.ofImage(screenshotImage)

        val targetLocations = locateAll(
                target, screenshotGrid, uniqueMatchLimit)

        if (targetLocations.isEmpty()) {
            return Result(null,
                    "Target not found")
        }
        else if (targetLocations.size > 1) {
            return Result(null,
                    "More than one target found: ${targetLocations.map { "[${it.x}, ${it.y}]" }}")
        }

        val targetLocation = targetLocations.single()

        val element = getElementByRectangle(
                targetLocation, screenshotGrid.width, driver)

        return Result(element, null)
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun locateAll(
        target: FeatureDocument,
        screenshotGrid: RgbGrid,
        limit: Int = Int.MAX_VALUE
    ): List<Rectangle> {
        val documentPath = target.objectLocation.documentPath
        val resourceListing = target.documentNotation.resources
            ?: error("Feature document has no resources: $documentPath")

        val sourceHistogram = TemplateMatcher.quantizedColorHistogram(screenshotGrid)

        val allMatches = mutableListOf<Rectangle>()

        for ((resourcePath, digest) in resourceListing.digests) {
            val cropGrid = cropGrid(
                ResourceLocation(documentPath, resourcePath), digest)

            allMatches.addAll(TemplateMatcher.locate(
                screenshotGrid, cropGrid, limit - allMatches.size, sourceHistogram))

            if (allMatches.size >= limit) {
                break
            }
        }

        return allMatches
    }


    private suspend fun cropGrid(
        resourceLocation: ResourceLocation,
        digest: Digest
    ): RgbGrid {
        synchronized(cropGridCache) {
            cropGridCache[digest]
        }?.let { return it }

        // Outside the lock (readResource suspends); a racing duplicate decode is harmless
        val cropPngBytes = notationMedia.readResource(resourceLocation)
        val cropImage = ImageIO.read(cropPngBytes.toInputStream())
        val cropGrid = RgbGrid.ofImage(cropImage)

        synchronized(cropGridCache) {
            cropGridCache[digest] = cropGrid
        }

        return cropGrid
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun getElementByRectangle(
        rectangle: Rectangle,
        screenshotWidth: Int,
        driver: RemoteWebDriver
    ): WebElement {
        val devicePixelRatio = (driver.executeScript(
            "return window.devicePixelRatio") as Number).toDouble()

        val viewportCssWidth = (driver.executeScript(
            "return window.innerWidth") as Number).toDouble()

        // Exotic drivers can screenshot at a scale other than the viewport DPR; the observed
        // width ratio is then the honest device-px to CSS-px factor.
        val dprConsistent =
            screenshotWidth == (viewportCssWidth * devicePixelRatio).roundToInt()

        val scale =
            if (dprConsistent) {
                1.0 / devicePixelRatio
            }
            else {
                viewportCssWidth / screenshotWidth
            }

        val cssX = rectangle.centerX * scale
        val cssY = rectangle.centerY * scale

        val element = driver.executeScript(
            "return document.elementFromPoint(arguments[0], arguments[1])", cssX, cssY)

        return element as? WebElement
            ?: error("No element at ($cssX, $cssY): " +
                "viewport width $viewportCssWidth, screenshot width $screenshotWidth, " +
                "devicePixelRatio $devicePixelRatio" +
                (if (dprConsistent) "" else " (width-ratio fallback)"))
    }
}
