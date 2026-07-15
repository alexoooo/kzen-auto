package tech.kzen.auto.test.server.script.step

import kotlinx.coroutines.delay
import org.openqa.selenium.OutputType
import org.openqa.selenium.remote.RemoteWebDriver
import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.service.webdriver.WebDriverSupport
import tech.kzen.auto.test.server.process.KzenAutoSubprocessRegistry
import tech.kzen.auto.test.server.process.SutHandle
import tech.kzen.lib.common.exec.BinaryExecutionValue
import tech.kzen.lib.common.reflect.Reflect


/**
 * Navigate the browser to a SUT that [StartKzenAutoStep] started, addressing it by [name] instead of by
 * URL, optionally under [path].
 *
 * This is what frees a SUT to run on an ephemeral port. `BrowserGetStep.location` is a static YAML
 * literal, so pointing it at a SUT forced the port to be written TWICE per file — once on the Start
 * step, once inside the URL — as two independent literals with nothing keeping them in sync. Reading
 * the port off the [SutHandle] the Start step registered with the engine removes both the duplication
 * and the hardcoding.
 *
 * Test-only, and deliberately NOT a subclass or hook of `BrowserGetStep`: a harness need must not
 * reshape shared kzen-auto-jvm code. The small overlap (delay + screenshot) is the price of that.
 */
@Reflect
class BrowserGetSutStep(
    private val name: String,
    private val path: String,
    private val screenshotDelayMilliseconds: Long
):
    ScriptStep
{
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.empty
    }


    override suspend fun run(execution: StepExecution): Any? {
        val driver = execution.resource(WebDriverSupport.resourceKey) as? RemoteWebDriver
            ?: error("Browser is not open")

        val sut = execution.resource(KzenAutoSubprocessRegistry.resourceKey(name)) as? SutHandle
            ?: error("SUT '$name' is not started - is there a preceding Start SUT step with this name?")

        driver.get("${sut.baseUrl}/${path.removePrefix("/")}")

        if (screenshotDelayMilliseconds > 0) {
            delay(screenshotDelayMilliseconds)
        }

        val screenshotPng = driver.getScreenshotAs(OutputType.BYTES)
        execution.traceDetail(BinaryExecutionValue(screenshotPng))

        return null
    }
}
