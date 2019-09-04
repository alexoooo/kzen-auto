package tech.kzen.auto.server.objects.feature

import tech.kzen.auto.common.paradigm.common.model.BinaryExecutionValue
import tech.kzen.auto.common.paradigm.common.model.NullExecutionValue
import tech.kzen.auto.common.paradigm.imperative.api.ExecutionAction
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeResult
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeSuccess
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Toolkit
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO


@Suppress("unused")
class ScreenshotTaker: ExecutionAction {
    override suspend fun perform(
            imperativeModel: ImperativeModel
    ): ImperativeResult {
        val robot = Robot()
        val screenshot = robot.createScreenCapture(
                Rectangle(Toolkit.getDefaultToolkit().screenSize))

        val buffer = ByteArrayOutputStream()
        ImageIO.write(screenshot, "png", buffer)
        val screenshotPng = buffer.toByteArray()

        return ImperativeSuccess(
                BinaryExecutionValue(screenshotPng),
                NullExecutionValue)
    }
}