package tech.kzen.auto.server.objects.script.step.browser

import kotlinx.coroutines.delay
import org.openqa.selenium.WebElement
import org.openqa.selenium.remote.RemoteWebDriver
import tech.kzen.auto.common.objects.document.target.TargetSpec
import tech.kzen.auto.server.service.target.TargetLocator
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service


@Reflect
class BrowserSubmitStep(
    target: TargetSpec,
    @Suppress("unused") selfLocation: ObjectLocation,
    @Service targetLocator: TargetLocator
):
    BrowserTargetStep(target, targetLocator)
{
    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun act(element: WebElement, driver: RemoteWebDriver): Any? {
        element.submit()

        delay(100)

        return null
    }
}
