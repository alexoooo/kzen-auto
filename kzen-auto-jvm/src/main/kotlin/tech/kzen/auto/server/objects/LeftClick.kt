package tech.kzen.auto.server.objects

import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import tech.kzen.auto.common.api.AutoAction
import tech.kzen.auto.server.service.ServerContext


class LeftClick(
        var xpath: String
) : AutoAction {
    override fun perform() {
        val driver = ServerContext.webDriverContext.get()

        val element: WebElement =
                driver.findElement(By.xpath(xpath))

        element.click()
    }
}