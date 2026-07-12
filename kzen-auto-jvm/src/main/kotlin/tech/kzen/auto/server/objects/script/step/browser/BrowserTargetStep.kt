package tech.kzen.auto.server.objects.script.step.browser

import org.openqa.selenium.OutputType
import org.openqa.selenium.WebElement
import org.openqa.selenium.remote.RemoteWebDriver
import tech.kzen.auto.common.objects.document.target.TargetSpec
import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.service.target.TargetLocator
import tech.kzen.auto.server.service.webdriver.WebDriverSupport
import tech.kzen.lib.common.exec.BinaryExecutionValue


/**
 * The shared frame of every browser step that acts on a located target: resolve the browser,
 * locate the target (failing with the locator's diagnostic, recording its match note), [act],
 * then screenshot the page as the step's trace detail (feeding the run's film strip).
 * A concrete step supplies only its action.
 */
abstract class BrowserTargetStep(
    private val target: TargetSpec,
    private val targetLocator: TargetLocator
): ScriptStep {
    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.empty
    }


    //-----------------------------------------------------------------------------------------------------------------
    final override suspend fun run(execution: StepExecution): Any? {
        val driver = execution.resource(WebDriverSupport.resourceKey) as? RemoteWebDriver
            ?: error("Browser is not open")

        val match = targetLocator.locateElement(target, driver)
        match.error?.let {
            error(it)
        }
        match.note?.let {
            execution.traceNote(it)
        }

        val result = act(match.webElement!!, driver)

        val screenshotPng = driver.getScreenshotAs(OutputType.BYTES)
        execution.traceDetail(BinaryExecutionValue(screenshotPng))

        return result
    }


    protected abstract suspend fun act(element: WebElement, driver: RemoteWebDriver): Any?
}
