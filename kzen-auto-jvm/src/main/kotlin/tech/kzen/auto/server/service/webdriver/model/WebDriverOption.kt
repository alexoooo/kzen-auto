package tech.kzen.auto.server.service.webdriver.model

import java.net.URI


data class WebDriverOption(
        val browserLauncher: BrowserLauncher,
        val operationSystem: OperatingSystem,
        val systemArchitecture: SystemArchitecture,
        val version: String,
        val download: URI
) {
    fun key(): String {
        return "${browserLauncher}_${operationSystem}_${systemArchitecture}_$version"
    }
}