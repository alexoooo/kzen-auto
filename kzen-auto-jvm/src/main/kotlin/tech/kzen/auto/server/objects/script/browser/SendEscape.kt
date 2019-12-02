package tech.kzen.auto.server.objects.script.browser

import org.openqa.selenium.Keys
import org.openqa.selenium.OutputType
import org.openqa.selenium.interactions.Actions
import tech.kzen.auto.common.paradigm.common.model.BinaryExecutionValue
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.common.model.NullExecutionValue
import tech.kzen.auto.common.paradigm.imperative.api.ExecutionAction
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.server.service.ServerContext


@Suppress("unused")
class SendEscape: ExecutionAction {
    override suspend fun perform(
            imperativeModel: ImperativeModel
    ): ExecutionResult {
        // https://groups.google.com/forum/#!topic/selenium-users/F2c4QWP50F8

        val driver = ServerContext.webDriverContext.get()

        Actions(driver).sendKeys(Keys.ESCAPE).build().perform()

        val screenshotPng = driver.getScreenshotAs(OutputType.BYTES)
        return ExecutionSuccess(
                NullExecutionValue,
                BinaryExecutionValue(screenshotPng))
    }
}