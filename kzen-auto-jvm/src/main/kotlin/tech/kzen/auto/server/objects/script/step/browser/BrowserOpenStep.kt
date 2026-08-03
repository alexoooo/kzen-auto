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
        // "Open a new browser window (existing one will be closed)": release any browser still bound to this
        // step's declared context BEFORE binding the new one, so the old driver is quit while it is still the
        // thing the name resolves to — rather than left to supersession, whose closer runs after the
        // replacement is already bound. Release runs the disposal attached below, so nothing here quits the
        // driver itself; a no-op when nothing is bound, which is the ordinary first-open case. Argument-free:
        // BrowserContext is this step's sole declaration (its `binds`) — a binder declares no `uses`, or the
        // spine's gate would fail it before it could ever open one. Offloaded because the disposal it triggers
        // is a blocking Selenium quit.
        execution.blocking { execution.releaseContext() }

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

        // Provide the browser as this step's declared BrowserContext: shared with the action steps (and any
        // hosted child Script), owned by the furthest document on the BrowserContext export chain — this one
        // when it exports nothing — and disposed per closePolicy at that owner's settle (or by an explicit
        // Close step).
        execution.bindContext(driver, closePolicy) {
            WebDriverSupport.quitQuietly(driver)
        }

        val infoText = execution.blocking { WebDriverManager.chromedriver().browserPath.orElse(null) }
        execution.traceDetail(infoText.toString())

        return null
    }
}
