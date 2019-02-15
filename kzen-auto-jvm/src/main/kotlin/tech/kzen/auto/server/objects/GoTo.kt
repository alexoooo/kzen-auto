package tech.kzen.auto.server.objects

import org.openqa.selenium.OutputType
import tech.kzen.auto.common.api.AutoAction
import tech.kzen.auto.common.exec.BinaryExecutionValue
import tech.kzen.auto.common.exec.ExecutionResult
import tech.kzen.auto.common.exec.ExecutionSuccess
import tech.kzen.auto.common.exec.NullExecutionValue
import tech.kzen.auto.server.service.ServerContext


@Suppress("unused")
class GoTo(
        var location: String
): AutoAction {
    override suspend fun perform(): ExecutionResult {
        val driver = ServerContext.webDriverContext.get()

        driver.get(location)

        val screenshotPng = driver.getScreenshotAs(OutputType.BYTES)

        return ExecutionSuccess(
                NullExecutionValue,
                BinaryExecutionValue(screenshotPng))
    }
}