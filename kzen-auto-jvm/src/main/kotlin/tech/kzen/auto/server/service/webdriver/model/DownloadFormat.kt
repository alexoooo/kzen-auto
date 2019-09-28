package tech.kzen.auto.server.service.webdriver.model

import java.net.URI


enum class DownloadFormat(
        val extension: String
) {
    Zip(".zip"),
    TarGz(".tar.gz");


    companion object {
        fun parse(location: URI): DownloadFormat {
            val path = location.path.toString()

            for (value in values()) {
                if (path.endsWith(value.extension)) {
                    return value
                }
            }

            throw IllegalArgumentException("Unknown format: $location")
        }
    }
}