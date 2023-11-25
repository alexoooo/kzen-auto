package tech.kzen.auto.server.service.compile

import org.jetbrains.kotlin.scripting.compiler.plugin.impl.ScriptJvmCompilerIsolated
import tech.kzen.auto.server.service.compile.KotlinCode.Companion.classNamePrefix
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.createCompilationConfigurationFromTemplate
import kotlin.script.experimental.jvm.compilationCache
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.updateClasspath
import kotlin.script.experimental.jvm.util.classpathFromClassloader
import kotlin.script.experimental.jvmhost.CompiledScriptJarsCache


@KotlinScript
class ScriptKotlinCompiler: KotlinCompiler {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val baseClassType: KotlinType = KotlinType(
            ScriptKotlinCompiler::class.java.kotlin)

        private val contextClass: KClass<*> = ScriptCompilationConfiguration::class.java.kotlin
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun compile(
        kotlinCode: KotlinCode,
        outputJarFile: Path,
        classpathLocations: List<Path>,
        classLoader: ClassLoader
    ):
        KotlinCompilerResult
    {
        Files.createDirectories(outputJarFile.parent)

        val scriptCompilationConfiguration = createCompilationConfigurationFromTemplate(
            baseClassType,
            defaultJvmScriptingHostConfiguration,
            contextClass
        ) {
            buildScriptCompilationConfiguration(
                classpathLocations,
                classLoader,
                outputJarFile)
        }

        val scriptCompilerProxy = ScriptJvmCompilerIsolated(defaultJvmScriptingHostConfiguration)

        val result = scriptCompilerProxy.compile(
            kotlinCode.toScriptSource(), scriptCompilationConfiguration)

        val errors = result.reports.filter { it.severity == ScriptDiagnostic.Severity.ERROR }

        val errorMessage = when {
            errors.isEmpty() -> null
            else -> errors.joinToString(" | ")
        }

        return when {
            errorMessage != null ->
                KotlinCompilerError(errorMessage)

            else ->
                KotlinCompilerSuccess(outputJarFile, classNamePrefix)
        }
    }


    private fun ScriptCompilationConfiguration.Builder.buildScriptCompilationConfiguration(
        classpathLocations: List<Path>,
        classLoader: ClassLoader,
        outputJarFile: Path
    ) {
        jvm {
            val classloaderClasspath: List<File> = classpathFromClassloader(classLoader, false)!!
            val classpathFiles = classloaderClasspath + classpathLocations.map { it.toFile() }
            updateClasspath(classpathFiles)
        }

        hostConfiguration(ScriptingHostConfiguration (defaultJvmScriptingHostConfiguration) {
            jvm {
                compilationCache(
                    CompiledScriptJarsCache { _, _ ->
                        outputJarFile.toFile()
                    }
                )
            }
        })
    }
}