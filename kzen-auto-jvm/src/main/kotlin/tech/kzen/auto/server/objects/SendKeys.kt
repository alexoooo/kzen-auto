package tech.kzen.auto.server.objects

import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import tech.kzen.auto.common.api.AutoAction
import tech.kzen.auto.common.exec.ExecutionResult
import tech.kzen.auto.common.exec.ExecutionSuccess
import tech.kzen.auto.server.service.ServerContext


@Suppress("unused")
class SendKeys(
        var xpath: String,
        var text: String
): AutoAction {
    override suspend fun perform(): ExecutionResult {
        // https://stackoverflow.com/questions/44455269/gmail-login-using-selenium-webdriver-in-java

        val driver = ServerContext.webDriverContext.get()

        val element: WebElement =
                driver.findElement(By.xpath(xpath))

        element.sendKeys(text)

        return ExecutionSuccess.empty
    }
}