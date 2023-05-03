package tech.kzen.auto.server.dev

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import tech.kzen.auto.server.KzenAutoConfig
import tech.kzen.auto.server.ktorMain
import tech.kzen.auto.server.kzenAutoInit
import tech.kzen.auto.server.kzenAutoJsModuleName
import java.nio.file.Path


fun main() {
    frontendDevelopmentMain(KzenAutoConfig(
        jsModuleName = kzenAutoJsModuleName,
        port = 8080,
        host = "127.0.0.1"
    ))
}


fun frontendDevelopmentMain(
    kzenAutoConfig: KzenAutoConfig
) {
    kzenAutoInit()

    System.setProperty("io.ktor.development", "true")

    val projectBaseDir = Path.of(".").toAbsolutePath().normalize()
    val jsDistDir = projectBaseDir.resolve(
        "${kzenAutoConfig.jsModuleName}/build/distributions")
    val jsFile = jsDistDir.resolve(kzenAutoConfig.jsFileName()).toFile()
    println("Auto-reload js file (exists = ${jsFile.exists()}): $jsFile")

    embeddedServer(
        Netty,
        port = kzenAutoConfig.port,
        host = kzenAutoConfig.host
    ) {
        routing {
            get(kzenAutoConfig.jsResourcePath()) {
                call.respondFile(jsFile)
            }
        }

        ktorMain(kzenAutoConfig)
    }.start(wait = true)
}