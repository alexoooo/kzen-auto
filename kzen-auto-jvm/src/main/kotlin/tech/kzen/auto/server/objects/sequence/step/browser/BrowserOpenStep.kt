package tech.kzen.auto.server.objects.sequence.step.browser

import io.github.bonigarcia.wdm.WebDriverManager
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.remote.RemoteWebDriver
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.objects.sequence.api.TracingSequenceStep
import tech.kzen.auto.server.objects.sequence.model.StepContext
import tech.kzen.auto.server.service.v1.model.LogicResult
import tech.kzen.auto.server.service.v1.model.LogicResultSuccess
import tech.kzen.auto.server.service.v1.model.tuple.TupleDefinition
import tech.kzen.auto.server.service.v1.model.tuple.TupleValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class BrowserOpenStep(
    selfLocation: ObjectLocation
):
    TracingSequenceStep(selfLocation)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun valueDefinition(): TupleDefinition {
        return TupleDefinition.empty
    }


    override fun continueOrStart(
        stepContext: StepContext
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

        KzenAutoContext.global().webDriverContext.set(driver)

        val infoText = WebDriverManager.chromedriver().browserPath.orElse(null)
        traceDetail(stepContext, infoText.toString())

        return LogicResultSuccess(TupleValue.empty)
    }
}