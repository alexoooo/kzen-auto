package tech.kzen.auto.server.dev

import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.ktorMain
import tech.kzen.auto.server.kzenAutoInit
import tech.kzen.auto.server.kzenAutoJsModuleName
import java.nio.file.Path


/**
 * Frontend dev server.
 *
 * Run it (rebuilds the JS bundle first, then serves the latest UI on refresh):
 * ./gradlew :kzen-auto-jvm:frontendDevelopment -PjsWatch
 *
 * `-PjsWatch` gives the unminified dev bundle (symbols, faster); omit it for the minified prod bundle.
 *
 * Hot reload loop: leave the server running and pair it with a watch:
 * ./gradlew -t :kzen-auto-js:jsEsbuildBundle -PjsWatch
 */
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
        "${context.config.jsModuleName}/build/dist/js/productionExecutable")
    val jsFile = jsDistDir.resolve(context.config.jsFileName()).toFile()
    println("Auto-reload js file (exists = ${jsFile.exists()}): $jsFile")

    embeddedServer(
        Netty,
        port = context.config.port,
        host = context.config.host
    ) {
        routing {
            get(context.config.jsResourcePath()) {
                // Dev server: never let the browser cache the bundle. Without this, the JS route sends only
                // Last-Modified, so browsers heuristically cache it and serve stale JS on a plain reload
                // (the "needs a second reload to see my change" symptom). no-store forces a refetch every
                // load, so a single reload always reflects the latest esbuild output. Dev-only by construction.
                call.response.header(HttpHeaders.CacheControl, "no-store")
                call.respondFile(jsFile)
            }
        }

        ktorMain(context)
    }.start(wait = true)
}