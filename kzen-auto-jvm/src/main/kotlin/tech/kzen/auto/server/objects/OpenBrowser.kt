package tech.kzen.auto.server.objects

import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import tech.kzen.auto.common.api.AutoAction
import tech.kzen.auto.server.service.ServerContext
import tech.kzen.auto.server.service.webdriver.model.BrowserLauncher


@Suppress("unused")
class OpenBrowser: AutoAction {
    override suspend fun perform() {
        if (ServerContext.webDriverContext.present()) {
            return
        }

        val webDriverOption = ServerContext.webDriverRepo.latest(BrowserLauncher.GoogleChrome)
        println("webDriverOption: $webDriverOption")

        val binary = ServerContext.webDriverInstaller.install(webDriverOption)

        System.setProperty(
                webDriverOption.browserLauncher.driverSystemProperty,
                binary.toString())

        // https://stackoverflow.com/a/44884633/1941359
        val chromeOptions = ChromeOptions()
        chromeOptions.setExperimentalOption("useAutomationExtension", false)

        val driver = ChromeDriver(chromeOptions)

        ServerContext.webDriverContext.set(driver)
    }
}