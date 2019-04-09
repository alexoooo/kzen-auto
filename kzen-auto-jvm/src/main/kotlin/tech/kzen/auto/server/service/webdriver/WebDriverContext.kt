package tech.kzen.auto.server.service.webdriver

import org.openqa.selenium.remote.RemoteWebDriver
import org.openqa.selenium.remote.UnreachableBrowserException


class WebDriverContext {
    private var webDriver: RemoteWebDriver? = null


    fun present(): Boolean {
        return webDriver != null
    }


    private fun clear() {
        webDriver = null
    }


    fun set(webDriver: RemoteWebDriver) {
        if (present()) {
            quit()
        }

        this.webDriver = webDriver
    }


    fun quit() {
        if (! present()) {
            return
        }

        try {
            get().quit()
        }
        catch (ignored: UnreachableBrowserException) {
            // https://stackoverflow.com/a/47936386/1941359
        }

        clear()
    }


    fun get(): RemoteWebDriver {
        // TODO: has been observed to fail
        check(webDriver != null) {"WebDriver missing"}
        return webDriver!!
    }
}