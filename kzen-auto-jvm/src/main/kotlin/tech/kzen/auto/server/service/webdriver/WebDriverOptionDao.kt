package tech.kzen.auto.server.service.webdriver

import tech.kzen.auto.server.service.webdriver.model.BrowserLauncher
import tech.kzen.auto.server.service.webdriver.model.CpuArchitecture
import tech.kzen.auto.server.service.webdriver.model.OperatingSystem
import tech.kzen.auto.server.service.webdriver.model.WebDriverOption
import java.net.URI


// TODO: test on Mac
// TODO: check for "latest" version
class WebDriverOptionDao {
    // NB: chromedriver distribution has changed - https://stackoverflow.com/a/55266105
    private val options: List<WebDriverOption> = listOf(
//            WebDriverOption(
//                    BrowserLauncher.GoogleChrome,
//                    OperatingSystem.Linux,
//                    CpuArchitecture.X86_64,
//                    "77.0.3865.40",
//                    URI("https://chromedriver.storage.googleapis.com/77.0.3865.40/chromedriver_linux64.zip")
//            ),
            WebDriverOption(
                    BrowserLauncher.Firefox,
                    OperatingSystem.Linux,
                    CpuArchitecture.X86_64,
                    "0.27.0",
                    URI("https://github.com/mozilla/geckodriver/releases/download/v0.27.0/geckodriver-v0.27.0-linux64.tar.gz")
            ),

            WebDriverOption(
                    BrowserLauncher.GoogleChrome,
                    OperatingSystem.Windows,
                    CpuArchitecture.X86_32,
                    "86.0.4240.22",
                    URI("https://chromedriver.storage.googleapis.com/86.0.4240.22/chromedriver_win32.zip")
            ),
            WebDriverOption(
                    BrowserLauncher.GoogleChrome,
                    OperatingSystem.Windows,
                    CpuArchitecture.X86_64,
                    "86.0.4240.22",
                    URI("https://chromedriver.storage.googleapis.com/86.0.4240.22/chromedriver_win32.zip")
            ),

            WebDriverOption(
                    BrowserLauncher.GoogleChrome,
                    OperatingSystem.OsX,
                    CpuArchitecture.X86_64,
                    "86.0.4240.22",
                    URI("https://chromedriver.storage.googleapis.com/86.0.4240.22/chromedriver_mac64.zip")
            ))


    fun latest(
            browser: BrowserLauncher = BrowserLauncher.GoogleChrome,
            os: OperatingSystem = OperatingSystem.get(),
            architecture: CpuArchitecture = CpuArchitecture.get()
    ): WebDriverOption? {
        val allVersions = options.filter {
            it.browserLauncher == browser &&
                    it.operationSystem == os &&
                    it.cpuArchitecture == architecture
        }

        return allVersions.lastOrNull()
    }
}