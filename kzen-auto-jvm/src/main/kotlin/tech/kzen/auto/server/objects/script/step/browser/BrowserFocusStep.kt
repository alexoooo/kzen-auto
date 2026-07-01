package tech.kzen.auto.server.objects.script.step.browser

import org.openqa.selenium.Keys
import org.openqa.selenium.OutputType
import org.openqa.selenium.remote.RemoteWebDriver
import tech.kzen.auto.common.objects.document.feature.TargetSpec
import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.service.vision.VisionUtils
import tech.kzen.auto.server.service.webdriver.WebDriverSupport
import tech.kzen.lib.common.exec.BinaryExecutionValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.media.NotationMedia


@Reflect
class BrowserFocusStep(
    private val target: TargetSpec,
    @Suppress("unused") selfLocation: ObjectLocation,
    @Service private val notationMedia: NotationMedia
):
    ScriptStep
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.empty
    }


    override suspend fun run(execution: StepExecution): Any? {
        val driver = execution.resource(WebDriverSupport.resourceKey) as? RemoteWebDriver
            ?: error("Browser is not open")

        val match = VisionUtils.locateElement(target, driver, notationMedia)
        match.error?.let {
            error(it)
        }

        val element = match.webElement!!

        // https://stackoverflow.com/questions/11337353/correct-way-to-focus-an-element-in-selenium-webdriver-using-java
        element.sendKeys(Keys.SHIFT)
        driver.executeScript("element.focus();")

        val screenshotPng = driver.getScreenshotAs(OutputType.BYTES)
        execution.traceDetail(BinaryExecutionValue(screenshotPng))

        return null
    }
}
