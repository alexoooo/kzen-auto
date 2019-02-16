package tech.kzen.auto.server.objects

import org.openqa.selenium.By
import org.openqa.selenium.OutputType
import org.openqa.selenium.WebElement
import tech.kzen.auto.common.api.AutoAction
import tech.kzen.auto.common.exec.BinaryExecutionValue
import tech.kzen.auto.common.exec.ExecutionResult
import tech.kzen.auto.common.exec.ExecutionSuccess
import tech.kzen.auto.common.exec.NullExecutionValue
import tech.kzen.auto.server.service.ServerContext


@Suppress("unused")
class FormSubmit(
        var xpath: String
): AutoAction {
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