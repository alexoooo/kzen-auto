package tech.kzen.auto.server.objects.script.step.browser

import org.openqa.selenium.Keys
import org.openqa.selenium.WebElement
import org.openqa.selenium.remote.RemoteWebDriver
import tech.kzen.auto.common.objects.document.target.TargetSpec
import tech.kzen.auto.server.service.target.TargetLocator
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service


@Reflect
class BrowserFocusStep(
    target: TargetSpec,
    @Suppress("unused") selfLocation: ObjectLocation,
    @Service targetLocator: TargetLocator
):
    BrowserTargetStep(target, targetLocator)
{
    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun act(element: WebElement, driver: RemoteWebDriver): Any? {
        // https://stackoverflow.com/questions/11337353/correct-way-to-focus-an-element-in-selenium-webdriver-using-java
        element.sendKeys(Keys.SHIFT)
        driver.executeScript("element.focus();")

        return null
    }
}
