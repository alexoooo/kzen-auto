package tech.kzen.auto.server.objects.script.step.browser

import io.github.bonigarcia.wdm.WebDriverManager
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.remote.RemoteWebDriver
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.TracingScriptStep
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.objects.script.model.ScriptExecutionContext
import tech.kzen.auto.server.service.webdriver.WebDriverContext
import tech.kzen.lib.common.exec.logic.ResourceClosePolicy
import tech.kzen.lib.common.exec.logic.model.LogicResult
import tech.kzen.lib.common.exec.logic.model.LogicResultSuccess
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service


@Reflect
class BrowserOpenStep(
    private val closePolicy: ResourceClosePolicy,
    selfLocation: ObjectLocation,
    @Service private val webDriverContext: WebDriverContext
):
    TracingScriptStep(selfLocation)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.empty
    }


    override fun continueOrStart(
        scriptExecutionContext: ScriptExecutionContext
    ): LogicResult {
        WebDriverManager.chromedriver().setup()

        // http://chromedriver.chromium.org/extensions
        // https://stackoverflow.com/a/44884633/1941359
        val chromeOptions = ChromeOptions()

        // TODO: deprecated?
//        chromeOptions.setExperimentalOption("useAutomationExtension", false)

//        // https://www.maketecheasier.com/download-save-chrome-extension/
//        for (extensionFile in extensionFiles) {
//            val asFile = Paths.get(extensionFile).toFile()
//            chromeOptions.addExtensions(asFile)
//        }

        // https://stackoverflow.com/questions/75678572/java-io-ioexception-invalid-status-code-403-text-forbidden
        chromeOptions.addArguments("--remote-allow-origins=*")

        // TODO: https://www.selenium.dev/documentation/webdriver/drivers/service/#setting-log-output
//        ChromeDriverService.Builder().build()
        val driver: RemoteWebDriver = ChromeDriver(chromeOptions)

        webDriverContext.set(driver)

        scriptExecutionContext.resourceScope.register(
            WebDriverContext.resourceKey, closePolicy
        ) {
            webDriverContext.quit()
        }

        val infoText = WebDriverManager.chromedriver().browserPath.orElse(null)
        traceDetail(scriptExecutionContext, infoText.toString())

        return LogicResultSuccess(TupleValue.empty)
    }
}