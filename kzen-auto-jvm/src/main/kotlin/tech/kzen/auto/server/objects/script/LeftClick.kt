package tech.kzen.auto.server.objects.script

import org.openqa.selenium.By
import org.openqa.selenium.OutputType
import org.openqa.selenium.WebElement
import tech.kzen.auto.common.paradigm.common.model.BinaryExecutionValue
import tech.kzen.auto.common.paradigm.common.model.NullExecutionValue
import tech.kzen.auto.common.paradigm.imperative.api.ExecutionAction
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeResult
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeSuccess
import tech.kzen.auto.server.service.ServerContext


@Suppress("unused")
class LeftClick(
        var xpath: String
): ExecutionAction {
    override suspend fun perform(): ImperativeResult {
        val driver = ServerContext.webDriverContext.get()

        val element: WebElement =
                driver.findElement(By.xpath(xpath))

        element.click()

        val screenshotPng = driver.getScreenshotAs(OutputType.BYTES)
        return ImperativeSuccess(
                NullExecutionValue,
                BinaryExecutionValue(screenshotPng))
    }
}