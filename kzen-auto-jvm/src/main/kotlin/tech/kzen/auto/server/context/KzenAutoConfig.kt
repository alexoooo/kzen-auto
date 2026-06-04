package tech.kzen.auto.server.context

import tech.kzen.auto.common.api.staticResourcePath
import java.nio.file.Path
import java.nio.file.Paths


data class KzenAutoConfig(
    val jsModuleName: String,
    val port: Int = 80,
    val host: String = "127.0.0.1",

    // Directory containing src/main/resources/notation, for processes whose cwd is not the
    //  module they serve (e.g. IDE-launched TesterMain); null = GradleLocator's cwd heuristic.
    val moduleRoot: Path? = null
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val serverPortPrefix = "--server.port="
        private val serverPortRegex = Regex(
            Regex.escape(serverPortPrefix) + "\\d+")

        const val moduleRootPrefix = "--module.root="

        fun readPort(args: Array<String>): Int? {
            val match = args
                .lastOrNull { it.matches(serverPortRegex) }
                ?: return null

            val portText = match.substring(serverPortPrefix.length)
            return portText.toInt()
        }

        fun readModuleRoot(args: Array<String>): Path? {
            val match = args
                .lastOrNull { it.startsWith(moduleRootPrefix) }
                ?: return null

            return Paths.get(match.substring(moduleRootPrefix.length))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun jsFileName(): String {
        return "$jsModuleName.js"
    }

    fun jsResourcePath(): String {
        return "$staticResourcePath/${jsFileName()}"
    }


    //-----------------------------------------------------------------------------------------------------------------
    // NB: set only by the dev server mains (FrontendDevelopment / BackendDevelopment), never by the
    //     packaged production entry point (KzenAutoMain) — so this gates dev-only page content out
    //     of production. Read at call time (not captured in the constructor): the property is set
    //     after the config is built but before any request is served.
    fun developmentMode(): Boolean {
        return System.getProperty("io.ktor.development") == "true"
    }
}