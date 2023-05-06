package tech.kzen.auto.server.objects.script.browser

import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.remote.RemoteWebDriver
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.imperative.api.ScriptStep
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.service.webdriver.model.BrowserLauncher
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.reflect.Reflect
import java.nio.file.Paths


@Reflect
class OpenBrowser(
        private val extensionFiles: List<String>
): ScriptStep {
    override suspend fun perform(
            imperativeModel: ImperativeModel,
            graphInstance: GraphInstance
    ): ExecutionResult {
        closeIfAlreadyOpen()

        val webDriverOption = KzenAutoContext.global().webDriverRepo.latest(BrowserLauncher.GoogleChrome)
                ?: KzenAutoContext.global().webDriverRepo.latest(BrowserLauncher.Firefox)
                ?: throw IllegalStateException("Unable to find browser for current OS / architecture")
//        println("webDriverOption: $webDriverOption")

        val binary = KzenAutoContext.global().webDriverInstaller.install(webDriverOption)

        System.setProperty(
                webDriverOption.browserLauncher.driverSystemProperty,
                binary.toString())

        val driver: RemoteWebDriver = when (webDriverOption.browserLauncher) {
            BrowserLauncher.GoogleChrome -> {
                // http://chromedriver.chromium.org/extensions
                // https://stackoverflow.com/a/44884633/1941359
                val chromeOptions = ChromeOptions()

                // TODO: deprecated?
//                chromeOptions.setExperimentalOption("useAutomationExtension", false)

                // https://www.maketecheasier.com/download-save-chrome-extension/
                for (extensionFile in extensionFiles) {
                    val asFile = Paths.get(extensionFile).toFile()
                    chromeOptions.addExtensions(asFile)
                }

                // https://stackoverflow.com/questions/75678572/java-io-ioexception-invalid-status-code-403-text-forbidden
                chromeOptions.addArguments("--remote-allow-origins=*")

                ChromeDriver(chromeOptions)
            }

            BrowserLauncher.Firefox -> {
                FirefoxDriver()
            }

//            else ->
//                throw IllegalStateException("Unknown web driver: $webDriverOption")
        }

        KzenAutoContext.global().webDriverContext.set(driver)

        return ExecutionSuccess.empty
    }


    private fun closeIfAlreadyOpen() {
        // https://github.com/alexoooo/kzen-shell/issues/11
        KzenAutoContext.global().webDriverContext.quit()
    }
}