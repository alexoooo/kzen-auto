package tech.kzen.auto.server.objects

import org.openqa.selenium.chrome.ChromeDriver
import tech.kzen.auto.common.api.AutoAction
import tech.kzen.auto.server.service.ServerContext


class OpenBrowser: AutoAction {
    override fun perform() {
        if (ServerContext.webDriverContext.present()) {
            return
        }

        val webDriverOption = ServerContext.webDriverRepo.latest()

        println("webDriverOption: $webDriverOption")

        val binary = ServerContext.webDriverInstaller.install(webDriverOption)

//        val options = ChromeOptions()
//        options.setBinary(binary.toFile())

        System.setProperty(
                webDriverOption.browserLauncher.driverSystemProperty,
                binary.toString())
        val driver = ChromeDriver()

        ServerContext.webDriverContext.set(driver)
    }
}