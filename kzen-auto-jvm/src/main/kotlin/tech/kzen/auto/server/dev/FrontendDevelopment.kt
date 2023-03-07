package tech.kzen.auto.server.dev

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import tech.kzen.auto.server.jsFileName
import tech.kzen.auto.server.jsResourcePath
import tech.kzen.auto.server.ktorMain
import tech.kzen.auto.server.kzenAutoInit
import java.nio.file.Path


fun main() {
    kzenAutoInit()

    System.setProperty("io.ktor.development", "true")

    val projectBaseDir = Path.of(".").toAbsolutePath().normalize()
    val jsDistDir = projectBaseDir.resolve("kzen-auto-js/build/distributions")
    val jsFile = jsDistDir.resolve(jsFileName).toFile()
    println("Auto-reload js file (exists = ${jsFile.exists()}): $jsFile")

    embeddedServer(
        Netty,
        port = 8080,
        host = "127.0.0.1"
    ) {
        routing {
            get(jsResourcePath) {
                call.respondFile(jsFile)
            }
        }

        ktorMain()
    }.start(wait = true)
}