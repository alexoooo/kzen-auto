package tech.kzen.auto.server.objects.script.browser

import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.OutputType
import tech.kzen.auto.common.objects.document.feature.FeatureDocument
import tech.kzen.auto.common.paradigm.common.model.BinaryExecutionValue
import tech.kzen.auto.common.paradigm.common.model.NullExecutionValue
import tech.kzen.auto.common.paradigm.imperative.api.ExecutionAction
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeError
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeResult
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeSuccess
import tech.kzen.auto.server.service.ServerContext
import tech.kzen.auto.server.service.vision.RgbGrid
import tech.kzen.lib.common.model.locate.ResourceLocation
import java.awt.Rectangle
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO


class VisualClick(
        private val target: FeatureDocument
): ExecutionAction {
    override suspend fun perform(
            imperativeModel: ImperativeModel
    ): ImperativeResult {
        val driver = ServerContext.webDriverContext.get()

        val documentPath = target.objectLocation.documentPath
        val resourceListing = target.documentNotation.resources!!

        val screenshotPngBytes = driver.getScreenshotAs(OutputType.BYTES)
        val screenshotImage = ImageIO.read(ByteArrayInputStream(screenshotPngBytes))
        val screenshotGrid = RgbGrid.ofImage(screenshotImage)

        val allMatches = mutableListOf<Rectangle>()

        for (resourcePath in resourceListing.digests.keys) {
            val resourceLocation = ResourceLocation(documentPath, resourcePath)
            val cropPngBytes = ServerContext.notationMedia.readResource(resourceLocation)
            val cropImage = ImageIO.read(ByteArrayInputStream(cropPngBytes))
            val cropGrid = RgbGrid.ofImage(cropImage)
            val cropMatches = locate(screenshotGrid, cropGrid)
            allMatches.addAll(cropMatches)
        }

        if (allMatches.isEmpty()) {
            return ImperativeError(
                    "Target not found")
        }
        else if (allMatches.size > 1) {
            return ImperativeError(
                    "More than one target found: ${allMatches.map { "[${it.x}, ${it.y}]"  }}")
        }

        val match = allMatches.single()
        val targetX = match.centerX
        val targetY = match.centerY

        val dimension = driver.manage().window().size
        val scaleRatio = dimension.width / screenshotImage.width.toDouble()
        val translateX = targetX * scaleRatio
        val translateY = targetY * scaleRatio

        // https://stackoverflow.com/a/45292071/1941359
        val jsExec = driver as JavascriptExecutor
        jsExec.executeScript("el = document.elementFromPoint($translateX, $translateY); el.click();")

        val postScreenshotPngBytes = driver.getScreenshotAs(OutputType.BYTES)
        return ImperativeSuccess(
                NullExecutionValue,
                BinaryExecutionValue(postScreenshotPngBytes))
    }


    private fun locate(source: RgbGrid, target: RgbGrid): List<Rectangle> {
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


    private fun matches(
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