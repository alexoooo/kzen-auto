package tech.kzen.auto.server.service.webdriver

import tech.kzen.auto.server.service.webdriver.model.BrowserLauncher
import tech.kzen.auto.server.service.webdriver.model.OperatingSystem
import tech.kzen.auto.server.service.webdriver.model.CpuArchitecture
import tech.kzen.auto.server.service.webdriver.model.WebDriverOption
import java.net.URI


class WebDriverOptionDao {
    val options: List<WebDriverOption> = listOf(
            WebDriverOption(
                    BrowserLauncher.GoogleChrome,
                    OperatingSystem.Linux,
                    CpuArchitecture.X86_64,
                    "2.41",
                    URI("https://chromedriver.storage.googleapis.com/2.41/chromedriver_linux64.zip")
            ),
            WebDriverOption(
                    BrowserLauncher.GoogleChrome,
                    OperatingSystem.Windows,
                    CpuArchitecture.X86_32,
                    "2.41",
                    URI("https://chromedriver.storage.googleapis.com/2.41/chromedriver_win32.zip")
            ),
            WebDriverOption(
                    BrowserLauncher.GoogleChrome,
                    OperatingSystem.OsX,
                    CpuArchitecture.X86_64,
                    "2.41",
                    URI("https://chromedriver.storage.googleapis.com/2.41/chromedriver_mac64.zip")
            ))


    fun latest(
            browser: BrowserLauncher = BrowserLauncher.GoogleChrome,
            os: OperatingSystem = OperatingSystem.get(),
            architecture: CpuArchitecture = CpuArchitecture.get()
    ): WebDriverOption {
        val allVersions = options.filter {
            it.browserLauncher == browser &&
                    it.operationSystem == os &&
                    it.cpuArchitecture == architecture
        }

        check(allVersions.isNotEmpty(), {"Not available: $browser / $os / $architecture"} )

        return allVersions.last()
    }
}