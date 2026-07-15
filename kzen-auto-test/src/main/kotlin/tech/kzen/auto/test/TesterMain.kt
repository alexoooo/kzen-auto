package tech.kzen.auto.test

import tech.kzen.auto.server.context.KzenAutoConfig
import tech.kzen.auto.test.codegen.KzenAutoTestModule
import tech.kzen.auto.test.server.process.KzenAutoSubprocessRegistry
import tech.kzen.lib.server.notation.locate.GradleLocator
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries


object TesterMain {
    // The INTERACTIVE tester's port: what a bare IDE gutter run (or `runTester`) binds when given no
    //  --server.port=, and what kzen-auto-test/AGENTS.md tells you to browse. Fixed on purpose — a
    //  bookmarkable URL is the whole point.
    //
    //  selfTest does NOT use this: SelfTestBase takes a free port instead, so a developer's own tester
    //  can sit here indefinitely without ever contending with a self-test run.
    const val TESTER_PORT = 18081

    private const val kzenAutoJarProperty = "kzenAutoJar"


    @JvmStatic
    fun main(args: Array<String>) {
        // Locate the kzen-auto-test module from this class's code source, so the launch context
        //  (bare IDE gutter run, runTester, selfTest) works with any working directory: notation
        //  (via --module.root) and fixtures (via KzenAutoConfig.moduleRoot) resolve against the
        //  module root instead of the cwd.
        val explicitModuleRoot = KzenAutoConfig.readModuleRoot(args)
        val moduleRoot = explicitModuleRoot
            ?: GradleLocator.moduleRootOfCodeSource(TesterMain::class.java)

        val extraArgs = mutableListOf<String>()
        if (explicitModuleRoot == null) {
            extraArgs.add(KzenAutoConfig.moduleRootPrefix + moduleRoot)
        }
        if (KzenAutoConfig.readPort(args) == null) {
            extraArgs.add(KzenAutoConfig.serverPortPrefix + TESTER_PORT)
        }

        if (System.getProperty(kzenAutoJarProperty) == null) {
            latestKzenAutoJar(moduleRoot)?.let {
                System.setProperty(kzenAutoJarProperty, it.toString())
            }
        }

        KzenAutoTestModule.register()

        Runtime.getRuntime().addShutdownHook(Thread({
            KzenAutoSubprocessRegistry.closeAll()
        }, "kzen-auto-test-subprocess-cleanup"))

        tech.kzen.auto.server.main(args + extraArgs)
    }


    /**
     * Latest locally-built kzen-auto fat jar, or null when not yet built —
     *  StartKzenAutoStep reads the property lazily and errors clearly there.
     */
    private fun latestKzenAutoJar(moduleRoot: Path): Path? {
        val libsDir = moduleRoot.resolve("../kzen-auto-jvm/build/libs").normalize()
        if (!Files.isDirectory(libsDir)) {
            return null
        }
        return libsDir
            .listDirectoryEntries("kzen-auto-jvm-*.jar")
            .maxByOrNull { Files.getLastModifiedTime(it) }
    }
}
