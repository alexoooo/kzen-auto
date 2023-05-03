package tech.kzen.auto.server.dev

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import tech.kzen.auto.server.KzenAutoConfig
import tech.kzen.auto.server.ktorMain
import tech.kzen.auto.server.kzenAutoInit
import tech.kzen.auto.server.kzenAutoJsModuleName


fun main() {
    backendDevelopmentMain(KzenAutoConfig(
        jsModuleName = kzenAutoJsModuleName,
        port = 8080,
        host = "127.0.0.1",
    ))
}


fun backendDevelopmentMain(
    kzenAutoConfig: KzenAutoConfig
) {
    kzenAutoInit()

    System.setProperty("io.ktor.development", "true")

    embeddedServer(
        Netty,
        port = kzenAutoConfig.port,
        host = kzenAutoConfig.host,
        watchPaths = listOf(
            "classes",
            "resources")
    ) {
        ktorMain(kzenAutoConfig)
    }.start(wait = true)
}