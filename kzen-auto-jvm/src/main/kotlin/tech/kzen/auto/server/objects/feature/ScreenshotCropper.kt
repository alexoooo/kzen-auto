package tech.kzen.auto.server.objects.feature

import tech.kzen.auto.common.paradigm.imperative.api.ExecutionAction
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeResult


class ScreenshotCropper: ExecutionAction {
    override suspend fun perform(
            imperativeModel: ImperativeModel
    ): ImperativeResult {
        TODO()
//        val robot = Robot(GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice)
//        val rect = Rectangle(Toolkit.getDefaultToolkit().screenSize)
//        val screenshot = robot.createScreenCapture(rect)
//
//        val buffer = ByteArrayOutputStream()
//        ImageIO.write(screenshot, "png", buffer)
//        val screenshotPng = buffer.toByteArray()
//
//        return ImperativeSuccess(
//                BinaryExecutionValue(screenshotPng),
//                NullExecutionValue)
    }
}