package tech.kzen.auto.server.objects.feature

import tech.kzen.auto.common.paradigm.common.model.BinaryExecutionValue
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.common.model.NullExecutionValue
import tech.kzen.auto.common.paradigm.detached.api.DetachedAction
import tech.kzen.auto.common.paradigm.detached.model.DetachedRequest
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Toolkit
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO




@Suppress("unused")
class ScreenshotTaker: DetachedAction {
    override suspend fun execute(
            request: DetachedRequest
    ): ExecutionResult {
        // NB: screenshots don't work with some OS/JVM combinations, see:
        //  https://stackoverflow.com/a/58086589

        val graphicsDevice = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
        val screenWidth = graphicsDevice.displayMode.width
        val screenHeight = graphicsDevice.displayMode.height

        val robot = Robot(graphicsDevice)
        val rect = Rectangle(Toolkit.getDefaultToolkit().screenSize)

        val multiResolutionScreenshot = robot.createMultiResolutionScreenCapture(rect)
        val screenshot = multiResolutionScreenshot.getResolutionVariant(
                screenWidth.toDouble(), screenHeight.toDouble())

        val buffer = ByteArrayOutputStream()
        ImageIO.write(screenshot as BufferedImage, "png", buffer)
        val screenshotPng = buffer.toByteArray()

        return ExecutionSuccess(
                BinaryExecutionValue(screenshotPng),
                NullExecutionValue)
    }
}