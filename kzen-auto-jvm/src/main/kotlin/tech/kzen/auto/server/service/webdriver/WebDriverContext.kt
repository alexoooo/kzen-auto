package tech.kzen.auto.server.service.webdriver

import org.openqa.selenium.WebDriver


class WebDriverContext {
    private var webDriver: WebDriver? = null


    fun present(): Boolean {
        return webDriver != null
    }


    fun set(webDriver: WebDriver) {
        if (present()) {
            quit()
        }

        this.webDriver = webDriver
    }


    fun quit() {
        if (! present()) {
            return
        }

        get().quit()
        webDriver = null
    }


    fun get(): WebDriver {
        check(webDriver != null, {"WebDriver not found"})
        return webDriver!!
    }
}