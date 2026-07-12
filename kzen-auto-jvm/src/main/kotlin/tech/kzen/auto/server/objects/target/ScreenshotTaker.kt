package tech.kzen.auto.server.objects.target

import tech.kzen.auto.common.paradigm.detached.DetachedAction
import tech.kzen.lib.common.exec.*
import tech.kzen.lib.common.reflect.Reflect
import java.awt.AWTError
import java.awt.GraphicsEnvironment
import java.awt.HeadlessException
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Toolkit
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO


@Reflect
class ScreenshotTaker: DetachedAction {
    companion object {
        private const val headlessMessage = "Screen capture unavailable (headless server)"
    }


    override suspend fun execute(
        request: ExecutionRequest
    ): ExecutionResult {
        val screenshotPng =
            try {
                captureScreenPng()
            }
            catch (e: HeadlessException) {
                return ExecutionResult.failure(headlessMessage)
            }
            catch (e: AWTError) {
                return ExecutionResult.failure(headlessMessage)
            }

        return ExecutionSuccess(
                BinaryExecutionValue(screenshotPng),
                NullExecutionValue
        )
    }


    private fun captureScreenPng(): ByteArray {
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

        @Suppress("BlockingMethodInNonBlockingContext")
        ImageIO.write(screenshot as BufferedImage, "png", buffer)

        return buffer.toByteArray()
    }
}
