package tech.kzen.auto.server.objects

import org.openqa.selenium.OutputType
import tech.kzen.auto.common.paradigm.imperative.ExecutionAction
import tech.kzen.auto.common.paradigm.imperative.model.BinaryExecutionValue
import tech.kzen.auto.common.paradigm.imperative.model.ExecutionResult
import tech.kzen.auto.common.paradigm.imperative.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.imperative.model.NullExecutionValue
import tech.kzen.auto.server.service.ServerContext


@Suppress("unused")
class GoTo(
        var location: String
): ExecutionAction {
    override suspend fun perform(): ExecutionResult {
        val driver = ServerContext.webDriverContext.get()

        driver.get(location)

        val screenshotPng = driver.getScreenshotAs(OutputType.BYTES)
        return ExecutionSuccess(
                NullExecutionValue,
                BinaryExecutionValue(screenshotPng))
    }
}