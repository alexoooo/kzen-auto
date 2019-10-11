package tech.kzen.auto.server.objects.script.browser

import org.openqa.selenium.OutputType
import tech.kzen.auto.common.paradigm.common.model.BinaryExecutionValue
import tech.kzen.auto.common.paradigm.common.model.NullExecutionValue
import tech.kzen.auto.common.paradigm.imperative.api.ExecutionAction
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.server.service.ServerContext


@Suppress("unused")
class GoTo(
        private val location: String
): ExecutionAction {
    override suspend fun perform(
            imperativeModel: ImperativeModel
    ): ExecutionResult {
        val driver = ServerContext.webDriverContext.get()

        driver.get(location)

//        val filePng = driver.getScreenshotAs(OutputType.FILE)
//        println("screenshot: $filePng")
        val screenshotPng = driver.getScreenshotAs(OutputType.BYTES)

        return ExecutionSuccess(
                NullExecutionValue,
                BinaryExecutionValue(screenshotPng))
    }
}