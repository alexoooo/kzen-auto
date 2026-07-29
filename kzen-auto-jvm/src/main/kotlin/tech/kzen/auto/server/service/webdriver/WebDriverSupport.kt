package tech.kzen.auto.server.service.webdriver

import org.openqa.selenium.remote.RemoteWebDriver
import org.openqa.selenium.remote.UnreachableBrowserException


/**
 * Shared helpers for the browser-automation Script steps. The live driver is no longer a process singleton
 * (the former WebDriverContext) but a run-scoped resource the steps reach through the typed `BrowserContext`
 * declaration — provided by [tech.kzen.auto.server.objects.script.step.browser.BrowserOpenStep], read by the
 * action steps, disposed per its `closePolicy` when the OWNING document settles (the nearest one declaring a
 * `BrowserContext` slot, else the providing one), or by an explicit Close step.
 *
 * [resourceKey] is retained as the raw-API name of that same registration — the `key:` the `BrowserContext`
 * notation object declares — so a plugin or fixture using the string-keyed escape hatch shares one
 * registration with the typed steps.
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
