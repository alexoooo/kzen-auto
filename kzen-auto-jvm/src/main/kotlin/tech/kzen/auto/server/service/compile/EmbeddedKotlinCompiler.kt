package tech.kzen.auto.server.service.compile

import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.config.KotlinSourceRoot
import org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment.Companion.createForProduction
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinToJVMBytecodeCompiler.analyzeAndGenerate
import org.jetbrains.kotlin.cli.jvm.config.JvmClasspathRoot
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.JvmTarget
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.script.experimental.jvm.util.KotlinJars
import kotlin.script.experimental.jvm.util.classpathFromClassloader


class EmbeddedKotlinCompiler {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val errorMessageStart = " error: "
        private const val warningMessageStart = " warning: "

        init {
            // NB: avoid IntelliJ error
            setIdeaIoUseFallback()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private class DummyDisposable: Disposable {
        override fun dispose() {}
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun tryCompileModule(
        moduleName: String,
        sourcePaths: List<String>,
        saveClassesDir: File,
        classpathLocations: List<File>,
        classLoader: ClassLoader
    ): String? {
        val errorStreamBytes = ByteArrayOutputStream()
        val errorStream = PrintStream(errorStreamBytes)

        val configuration = configureModuleCompiler(
            moduleName, sourcePaths, saveClassesDir, classpathLocations, classLoader, errorStream)

        val env: KotlinCoreEnvironment = createForProduction(
            DummyDisposable(), configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES)

        val result = analyzeAndGenerate(env)
        errorStream.flush()
        if (result != null) {
            return null
        }

        val errorStreamContents: String = errorStreamBytes.toString(Charsets.UTF_8)
        return extractErrorMessage(errorStreamContents)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun extractErrorMessage(compilerMessage: String): String {
        val builder = StringBuilder()
        val lines: List<String> = compilerMessage.lines()

        var errorSeen = false
        for (i in 1 until lines.size) {
            val line = lines[i]
            if (! errorSeen) {
                if (line.contains(errorMessageStart)) {
                    val start: Int = line.indexOf(errorMessageStart)
                    builder.append(line.substring(start + errorMessageStart.length))
                    errorSeen = true
                }
            }
            else {
                if (line.contains(errorMessageStart) || line.contains(warningMessageStart)) {
                    break
                }
                builder.append("\n").append(line)
            }
        }
        return builder.toString()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun configureModuleCompiler(
        moduleName: String,
        sourcePaths: List<String>,
        saveClassesDir: File,
        classpathLocations: List<File>,
        classLoader: ClassLoader,
        errStream: PrintStream
    ): CompilerConfiguration {
        val configuration = CompilerConfiguration()

        configuration.put(CommonConfigurationKeys.MODULE_NAME, moduleName)
        configuration.put(
            CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY,
            PrintingMessageCollector(errStream, MessageRenderer.PLAIN_FULL_PATHS, true))

        configuration.put(JVMConfigurationKeys.OUTPUT_DIRECTORY, saveClassesDir)
        configuration.put(JVMConfigurationKeys.JVM_TARGET, JvmTarget.JVM_14)

        addClasspathConfiguration(configuration, classpathLocations, classLoader)

        for (sourcePath in sourcePaths) {
            configuration.add(CLIConfigurationKeys.CONTENT_ROOTS, KotlinSourceRoot(sourcePath, false))
        }
        return configuration
    }


    private fun addClasspathConfiguration(
        configuration: CompilerConfiguration,
        classpathLocations: List<File>,
        classLoader: ClassLoader
    ) {
        val classloaderClasspath: List<File> = classpathFromClassloader(classLoader, false)!!
        val classPath: MutableSet<File> = LinkedHashSet(classloaderClasspath)

        classPath.add(KotlinJars.stdlib)
        classPath.addAll(classpathLocations)

        for (file in classPath) {
            configuration.add(CLIConfigurationKeys.CONTENT_ROOTS, JvmClasspathRoot(file))
        }
    }
}