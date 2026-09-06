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
    val moduleRoot: Path? = null,

    // Managed-child lifeline flags, set only by a spawning harness (the kzen-auto-test tester,
    //  kzen-shell), never for interactive `java -jar` runs. When managedLifeline is true the process
    //  self-terminates on stdin EOF or a "SHUTDOWN" sentinel line; parentPid, when present, adds a
    //  ProcessHandle.onExit backup that reaps this child if the parent dies. Together they bind this
    //  child's lifetime to its parent's on every OS (see KzenAutoMain.startManagedLifeline).
    val managedLifeline: Boolean = false,
    val parentPid: Long? = null,

    // Version + build timestamp of the running artifact, loaded from a baked-in classpath resource
    //  by the entry point (see BuildInfo). Surfaced to the client via indexPage as logo hover text.
    val buildInfo: BuildInfo? = null,

    // This context's work root (transient Job scratch, persisted Worker output, compiler cache, schema cache).
    //  Created, canonicalized and claimed process-wide at context creation, so two live contexts never share
    //  one; null = the standalone default `../work` beside the module.
    val workRoot: Path? = null,

    // Services an embedding host supplies to @Service constructor parameters (see KzenAutoHost); empty standalone.
    val hostServices: KzenAutoHost = KzenAutoHost.empty,

    // Whether the cwd-relative `logs/` directory is presented as a managed storage area. A foreign host owns
    //  its own logging backend and location, so it turns this off; standalone kzen (bundled logback.xml) keeps it.
    val manageLogs: Boolean = true
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // --server.port= parsing is intentionally duplicated across the spawn protocol's mains
        //  (kzen-launcher's KzenLauncherConfig, kzen-shell's KzenShellProperties — no shared module);
        //  keep the copies in sync.
        const val serverPortPrefix = "--server.port="
        private val serverPortRegex = Regex(
            Regex.escape(serverPortPrefix) + "\\d+")

        const val moduleRootPrefix = "--module.root="

        const val managedLifelinePrefix = "--managed.lifeline="
        private const val managedLifelineStdin = "stdin"

        const val parentPidPrefix = "--parent.pid="

        // Plugin root for the process-global runtime (see KzenAutoRuntimeConfig); a runtime concern rather than
        //  a per-context one, parsed here beside the other command-line arguments for one place to look.
        const val pluginRootPrefix = "--plugin.root="

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

        const val workRootPrefix = "--work.root="

        fun readWorkRoot(args: Array<String>): Path? {
            val match = args
                .lastOrNull { it.startsWith(workRootPrefix) }
                ?: return null

            return Paths.get(match.substring(workRootPrefix.length))
        }

        fun readPluginRoot(args: Array<String>): Path? {
            val match = args
                .lastOrNull { it.startsWith(pluginRootPrefix) }
                ?: return null

            return Paths.get(match.substring(pluginRootPrefix.length))
        }

        fun readManagedLifeline(args: Array<String>): Boolean {
            val match = args
                .lastOrNull { it.startsWith(managedLifelinePrefix) }
                ?: return false

            return match.substring(managedLifelinePrefix.length) == managedLifelineStdin
        }

        fun readParentPid(args: Array<String>): Long? {
            val match = args
                .lastOrNull { it.startsWith(parentPidPrefix) }
                ?: return null

            return match.substring(parentPidPrefix.length).toLongOrNull()
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