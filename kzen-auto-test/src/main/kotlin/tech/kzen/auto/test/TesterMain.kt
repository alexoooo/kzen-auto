package tech.kzen.auto.test

import tech.kzen.auto.server.context.KzenAutoConfig
import tech.kzen.auto.test.codegen.KzenAutoTestModule
import tech.kzen.auto.test.server.process.KzenAutoSubprocessRegistry
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.listDirectoryEntries


object TesterMain {
    // Hardcoded port — BrowserGetStep.location is a static YAML field that cannot yet
    //  interpolate Script parameters, so the tester URL must match what the test-suite
    //  YAMLs hard-code. Swap to FreePort once interpolation lands.
    const val TESTER_PORT = 18081

    private const val kzenAutoJarProperty = "kzenAutoJar"


    @JvmStatic
    fun main(args: Array<String>) {
        // Locate the kzen-auto-test module from this class's code source, so the launch context
        //  (bare IDE gutter run, runTester, selfTest) works with any working directory: notation
        //  (via --module.root) and fixtures (via KzenAutoConfig.moduleRoot) resolve against the
        //  module root instead of the cwd.
        val explicitModuleRoot = KzenAutoConfig.readModuleRoot(args)
        val moduleRoot = explicitModuleRoot ?: locateModuleRoot()

        val extraArgs = mutableListOf<String>()
        if (explicitModuleRoot == null) {
            extraArgs.add(KzenAutoConfig.moduleRootPrefix + moduleRoot)
        }
        if (KzenAutoConfig.readPort(args) == null) {
            extraArgs.add("--server.port=$TESTER_PORT")
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
     * Walks up from this class's code source (classes dir in IDE/Gradle builds, jar under
     *  build/libs in selfTest) to the directory hosting the test-suite notation.
     */
    private fun locateModuleRoot(): Path {
        val codeSource = TesterMain::class.java.protectionDomain.codeSource?.location
            ?: error("TesterMain code source unavailable")

        var dir: Path? = Paths.get(codeSource.toURI()).parent
        while (dir != null) {
            if (Files.isDirectory(dir.resolve("src/main/resources/notation"))) {
                return dir
            }
            dir = dir.parent
        }

        error("kzen-auto-test module root not found above: $codeSource")
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
