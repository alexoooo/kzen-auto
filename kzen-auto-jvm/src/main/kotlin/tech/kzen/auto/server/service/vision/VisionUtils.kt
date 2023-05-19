package tech.kzen.auto.server.service.vision

import kotlinx.coroutines.runBlocking
import org.openqa.selenium.By
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.OutputType
import org.openqa.selenium.WebElement
import org.openqa.selenium.remote.RemoteWebDriver
import tech.kzen.auto.common.objects.document.feature.*
import tech.kzen.lib.common.model.locate.ResourceLocation
import tech.kzen.lib.common.service.media.NotationMedia
import tech.kzen.lib.platform.toInputStream
import java.awt.Rectangle
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO


object VisionUtils {
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


    fun locateElement(
        target: TargetSpec,
        driver: RemoteWebDriver,
        notationMedia: NotationMedia
    ): Result {
        val element = when (target) {
            FocusTarget ->
                driver.switchTo().activeElement()

            is TextTarget -> {
                val xpathEscaped = xpathEscape(target.text)

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
                val targetLocation = locateElement(
                        target.feature, driver, notationMedia)

                if (targetLocation.isError()) {
                    return targetLocation
                }

                targetLocation.webElement!!
            }
        }

        return Result(element, null)
    }


    fun xpathEscape(value: String): String {
        // https://stackoverflow.com/a/38254661/1941359
        return when {
            "'" !in value ->
                "'$value'"

            "\"" !in value ->
                '"' + value + '"'

            else ->
                "concat('${
                    value.replace("'", """',"'",'""")
                }')"
        }
    }


    fun locateElement(
            target: FeatureDocument,
            driver: RemoteWebDriver,
            notationMedia: NotationMedia
    ): Result {
        val screenshotPngBytes = driver.getScreenshotAs(OutputType.BYTES)
        val screenshotImage = ImageIO.read(ByteArrayInputStream(screenshotPngBytes))
        val screenshotGrid = RgbGrid.ofImage(screenshotImage)

        val targetLocations = locateAll(
                target, screenshotGrid, notationMedia)

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


    fun getElementByRectangle(
            rectangle: Rectangle,
            screenshotWidth: Int,
            driver: RemoteWebDriver
    ): WebElement {
        val targetX = rectangle.centerX
        val targetY = rectangle.centerY

        val dimension = driver.manage().window().size
        val scaleRatio = dimension.width / screenshotWidth.toDouble()
        val translateX = targetX * scaleRatio
        val translateY = targetY * scaleRatio

        val randomClassName = "_" + Math.random().toString().replace(".", "")

        // https://stackoverflow.com/a/45292071/1941359
        val jsExec = driver as JavascriptExecutor

        jsExec.executeScript(
                "el = document.elementFromPoint($translateX, $translateY);" +
                        "el.classList.add('$randomClassName');")

        val element = driver.findElement(By.className(randomClassName))

        // TODO: clean up randomClassName, the below doesn't work?
//        jsExec.executeScript(
//                "el = document.getElementsByClassName('$randomClassName')[0];" +
//                        "el.classList.remove('$randomClassName');")

        return element
    }


    fun locateAll(
            target: FeatureDocument,
            screenshotGrid: RgbGrid,
            notationMedia: NotationMedia
    ): List<Rectangle> {
        val documentPath = target.objectLocation.documentPath
        val resourceListing = target.documentNotation.resources!!

        val allMatches = mutableListOf<Rectangle>()

        for (resourcePath in resourceListing.digests.keys) {
            val resourceLocation = ResourceLocation(documentPath, resourcePath)
            val cropPngBytes = runBlocking {
                notationMedia.readResource(resourceLocation)
            }
            val cropImage = ImageIO.read(cropPngBytes.toInputStream())
            val cropGrid = RgbGrid.ofImage(cropImage)
            val cropMatches = locate(screenshotGrid, cropGrid)
            allMatches.addAll(cropMatches)
        }

        return allMatches
    }


    fun locate(source: RgbGrid, target: RgbGrid): List<Rectangle> {
        val matches = mutableListOf<Rectangle>()

        for (offsetX in 0 until source.width) {
            for (offsetY in 0 until source.height) {
                if (matches(source, target, offsetX, offsetY)) {
                    matches.add(Rectangle(
                            offsetX, offsetY, target.width, target.height))
                }
            }
        }

        return matches
    }


    fun matches(
            source: RgbGrid,
            target: RgbGrid,
            offsetX: Int,
            offsetY: Int
    ): Boolean {
        if (offsetX + target.width > source.width ||
                offsetY + target.height > source.height) {
            return false
        }

        for (x in 0 until target.width) {
            for (y in 0 until target.height) {
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