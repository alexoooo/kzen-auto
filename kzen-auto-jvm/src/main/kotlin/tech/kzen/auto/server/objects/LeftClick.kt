package tech.kzen.auto.server.objects

import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import tech.kzen.auto.common.api.AutoAction
import tech.kzen.auto.common.exec.ExecutionResult
import tech.kzen.auto.common.exec.ExecutionSuccess
import tech.kzen.auto.server.service.ServerContext


@Suppress("unused")
class LeftClick(
        var xpath: String
): AutoAction {
    override suspend fun perform(): ExecutionResult {
        val driver = ServerContext.webDriverContext.get()

        val element: WebElement =
                driver.findElement(By.xpath(xpath))

        element.click()

        return ExecutionSuccess.empty
    }
}