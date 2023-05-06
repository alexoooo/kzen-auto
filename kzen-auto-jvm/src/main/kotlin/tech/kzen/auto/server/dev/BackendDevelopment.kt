package tech.kzen.auto.server.dev

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.ktorMain
import tech.kzen.auto.server.kzenAutoInit
import tech.kzen.auto.server.kzenAutoJsModuleName


fun main(args: Array<String>) {
    val context = kzenAutoInit(args, kzenAutoJsModuleName)
    backendDevelopmentMain(context)
}


fun backendDevelopmentMain(
    context: KzenAutoContext
) {
    System.setProperty("io.ktor.development", "true")

    embeddedServer(
        Netty,
        port = context.config.port,
        host = context.config.host,
        watchPaths = listOf(
            "classes",
            "resources")
    ) {
        ktorMain(context)
    }.start(wait = true)
}