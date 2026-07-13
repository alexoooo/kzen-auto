package tech.kzen.auto.server.objects.script.step.browser

import org.openqa.selenium.WebElement
import org.openqa.selenium.remote.RemoteWebDriver
import tech.kzen.auto.common.objects.document.target.TargetSpec
import tech.kzen.auto.server.service.target.TargetLocator
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service


@Reflect
class BrowserClickStep(
    target: TargetSpec,
    delaySeconds: Double,
    @Service targetLocator: TargetLocator
):
    BrowserTargetStep(target, delaySeconds, targetLocator)
{
    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun act(element: WebElement, driver: RemoteWebDriver): Any? {
        if (element.tagName.lowercase() == "input" &&
                element.getAttribute("type")?.lowercase() == "submit") {
            element.submit()
        }
        else {
            element.click()
        }
        return null
    }
}
