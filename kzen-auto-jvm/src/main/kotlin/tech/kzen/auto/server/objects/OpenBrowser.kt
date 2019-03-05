package tech.kzen.auto.server.objects

import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import tech.kzen.auto.common.paradigm.imperative.ExecutionAction
import tech.kzen.auto.common.paradigm.imperative.model.ExecutionResult
import tech.kzen.auto.common.paradigm.imperative.model.ExecutionSuccess
import tech.kzen.auto.server.service.ServerContext
import tech.kzen.auto.server.service.webdriver.model.BrowserLauncher
import java.nio.file.Paths


@Suppress("unused")
class OpenBrowser(
        private val extensionFiles: List<String>
): ExecutionAction {
    override suspend fun perform(): ExecutionResult {
        closeIfAlreadyOpen()

        val webDriverOption = ServerContext.webDriverRepo.latest(BrowserLauncher.GoogleChrome)
//        println("webDriverOption: $webDriverOption")

        val binary = ServerContext.webDriverInstaller.install(webDriverOption)

        System.setProperty(
                webDriverOption.browserLauncher.driverSystemProperty,
                binary.toString())

        // http://chromedriver.chromium.org/extensions
        // https://stackoverflow.com/a/44884633/1941359
        val chromeOptions = ChromeOptions()
        chromeOptions.setExperimentalOption("useAutomationExtension", false)

        // https://www.maketecheasier.com/download-save-chrome-extension/
        for (extensionFile in extensionFiles) {
            val asFile = Paths.get(extensionFile).toFile()
            chromeOptions.addExtensions(asFile)
        }

        val driver = ChromeDriver(chromeOptions)

        ServerContext.webDriverContext.set(driver)

        return ExecutionSuccess.empty
    }


    private fun closeIfAlreadyOpen() {
        // https://github.com/alexoooo/kzen-shell/issues/11
        ServerContext.webDriverContext.quit()
    }
}