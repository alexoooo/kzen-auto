package tech.kzen.auto.server.service.webdriver

import tech.kzen.auto.server.service.webdriver.model.BrowserLauncher
import tech.kzen.auto.server.service.webdriver.model.OperatingSystem
import tech.kzen.auto.server.service.webdriver.model.SystemArchitecture
import tech.kzen.auto.server.service.webdriver.model.WebDriverOption
import java.net.URI


class WebDriverSelection {
    val options: List<WebDriverOption> = listOf(
            WebDriverOption(
                    BrowserLauncher.GoogleChrome,
                    OperatingSystem.Linux,
                    SystemArchitecture.X86_64,
                    "2.37",
                    URI("https://chromedriver.storage.googleapis.com/2.37/chromedriver_linux64.zip")
            ))


    fun latest(
            browser: BrowserLauncher = BrowserLauncher.GoogleChrome,
            os: OperatingSystem = OperatingSystem.get(),
            architecture: SystemArchitecture = SystemArchitecture.get()
    ): WebDriverOption {
        val allVersions = options.filter {
            it.browserLauncher == browser &&
                    it.operationSystem == os &&
                    it.systemArchitecture == architecture
        }

        check(allVersions.isNotEmpty(), {"Not available: $browser / $os / $architecture"} )

        return allVersions.last()
    }
}