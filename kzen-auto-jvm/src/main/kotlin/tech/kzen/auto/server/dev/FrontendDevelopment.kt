package tech.kzen.auto.server.dev

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.ktorMain
import tech.kzen.auto.server.kzenAutoInit
import tech.kzen.auto.server.kzenAutoJsModuleName
import java.nio.file.Path


fun main(args: Array<String>) {
    val context = kzenAutoInit(args, kzenAutoJsModuleName)
    frontendDevelopmentMain(context)
}


fun frontendDevelopmentMain(
    context: KzenAutoContext
) {
    System.setProperty("io.ktor.development", "true")

    val projectBaseDir = Path.of(".").toAbsolutePath().normalize()
    val jsDistDir = projectBaseDir.resolve(
        "${context.config.jsModuleName}/build/distributions")
    val jsFile = jsDistDir.resolve(context.config.jsFileName()).toFile()
    println("Auto-reload js file (exists = ${jsFile.exists()}): $jsFile")

    embeddedServer(
        Netty,
        port = context.config.port,
        host = context.config.host
    ) {
        routing {
            get(context.config.jsResourcePath()) {
                call.respondFile(jsFile)
            }
        }

        ktorMain(context)
    }.start(wait = true)
}