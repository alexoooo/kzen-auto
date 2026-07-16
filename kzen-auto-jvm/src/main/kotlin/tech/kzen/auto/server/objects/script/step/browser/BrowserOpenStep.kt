package tech.kzen.auto.server.objects.script.step.browser

import io.github.bonigarcia.wdm.WebDriverManager
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.remote.RemoteWebDriver
import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.service.webdriver.WebDriverSupport
import tech.kzen.lib.common.exec.logic.ResourceClosePolicy
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class BrowserOpenStep(
    private val closePolicy: ResourceClosePolicy,
):
    ScriptStep
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.empty
    }


    override suspend fun run(execution: StepExecution): Any? {
        // "Open a new browser window (existing one will be closed)": dispose any browser still open under the key.
        (execution.resource(WebDriverSupport.resourceKey) as? RemoteWebDriver)?.let {
            execution.blocking { WebDriverSupport.quitQuietly(it) }
            execution.releaseResource(WebDriverSupport.resourceKey)
        }

        // Driver-manager setup (download / discovery) and the Chrome process launch are long blocking calls —
        // offload them off the engine dispatcher so a concurrent run's threads aren't starved and pause / cancel
        // stay responsive (a cancel interrupts a hung launch).
        val driver: RemoteWebDriver = execution.blocking {
            WebDriverManager.chromedriver().setup()

            // http://chromedriver.chromium.org/extensions - https://stackoverflow.com/a/44884633/1941359
            val chromeOptions = ChromeOptions()

            // https://stackoverflow.com/questions/75678572/java-io-ioexception-invalid-status-code-403-text-forbidden
            chromeOptions.addArguments("--remote-allow-origins=*")

            ChromeDriver(chromeOptions)
        }

        // Open the browser as a run-scoped resource: shared with the action steps (and any hosted child Script),
        // disposed per closePolicy when the run settles (or by an explicit Close step).
        execution.openResource(WebDriverSupport.resourceKey, driver, closePolicy) {
            WebDriverSupport.quitQuietly(driver)
        }

        val infoText = execution.blocking { WebDriverManager.chromedriver().browserPath.orElse(null) }
        execution.traceDetail(infoText.toString())

        return null
    }
}
