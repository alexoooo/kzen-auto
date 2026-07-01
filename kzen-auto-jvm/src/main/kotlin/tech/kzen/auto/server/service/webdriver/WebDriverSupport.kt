package tech.kzen.auto.server.service.webdriver

import org.openqa.selenium.remote.RemoteWebDriver
import org.openqa.selenium.remote.UnreachableBrowserException


/**
 * Shared constants / helpers for the browser-automation Script steps. The live driver is no longer a process
 * singleton (the former WebDriverContext) but a per-run resource keyed [resourceKey] in the Script run's
 * resource registry — opened by [tech.kzen.auto.server.objects.script.step.browser.BrowserOpenStep], read by
 * the action steps, disposed per its `closePolicy` when the run settles (or by an explicit Close step).
 */
object WebDriverSupport {
    const val resourceKey = "browser"


    /**
     * Quit a driver, swallowing the benign "browser already gone" error (https://stackoverflow.com/a/47936386).
     */
    fun quitQuietly(driver: RemoteWebDriver) {
        try {
            driver.quit()
        }
        catch (ignored: UnreachableBrowserException) {
        }
    }
}
