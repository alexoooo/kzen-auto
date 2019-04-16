package tech.kzen.auto.server.objects.script

import org.openqa.selenium.By
import org.openqa.selenium.OutputType
import org.openqa.selenium.WebElement
import tech.kzen.auto.common.paradigm.imperative.ExecutionAction
import tech.kzen.auto.common.paradigm.imperative.model.BinaryExecutionValue
import tech.kzen.auto.common.paradigm.imperative.model.ExecutionResult
import tech.kzen.auto.common.paradigm.imperative.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.imperative.model.NullExecutionValue
import tech.kzen.auto.server.service.ServerContext


@Suppress("unused")
class FormSubmit(
        var xpath: String
): ExecutionAction {
    override suspend fun perform(): ExecutionResult {
        val driver = ServerContext.webDriverContext.get()

        val element: WebElement =
                driver.findElement(By.xpath(xpath))

        element.submit()

        val screenshotPng = driver.getScreenshotAs(OutputType.BYTES)
        return ExecutionSuccess(
                NullExecutionValue,
                BinaryExecutionValue(screenshotPng))
    }
}