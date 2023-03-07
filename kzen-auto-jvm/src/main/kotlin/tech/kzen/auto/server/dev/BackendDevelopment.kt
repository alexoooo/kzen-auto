package tech.kzen.auto.server.dev

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import tech.kzen.auto.server.ktorMain
import tech.kzen.auto.server.kzenAutoInit


fun main() {
    kzenAutoInit()

    System.setProperty("io.ktor.development", "true")

    embeddedServer(
        Netty,
        port = 8080,
        host = "127.0.0.1",
        watchPaths = listOf(
            "classes",
            "resources")
    ) {
        ktorMain()
    }.start(wait = true)
}