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


// `open` so the generated script facade class (which the scripting compiler emits as a subclass of this
// template) is loadable: reflecting a generated expression's inferred type (ExpressionReturnTypeInference) resolves
// that facade, which a final base class would reject with IncompatibleClassChangeError.
@KotlinScript
open class ScriptKotlinCompiler: KotlinCompiler {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val baseClassType: KotlinType = KotlinType(
            ScriptKotlinCompiler::class.java.kotlin)

        private val contextClass: KClass<*> = ScriptCompilationConfiguration::class.java.kotlin

        /**
         * The single diagnostic to report, and the user-relative position it points at when it has one.
         *
         * The FIRST diagnostic landing inside the user's code wins: an inline caret must mark the earliest real
         * problem, and a K2 cascade routinely emits a wrapper-level artefact — an uninferrable type parameter
         * on the generated lambda — ahead of the actual cause. When no diagnostic is attributable, the LAST one
         * is reported, being the most specific end of such a cascade.
         */
        private fun selectError(errors: List<ScriptDiagnostic>, kotlinCode: KotlinCode): KotlinCompilerError {
            require(errors.isNotEmpty())

            val userCodeRegion = kotlinCode.userCodeRegion
            if (userCodeRegion != null) {
                val lineStartOffsets = lineStartOffsets(kotlinCode.sourceText)

                for (error in errors) {
                    val userCodeOffset = userCodeOffset(error, lineStartOffsets, userCodeRegion)
                        ?: continue

                    return KotlinCompilerError(render(error), userCodeOffset)
                }
            }

            return KotlinCompilerError(render(errors.last()))
        }


        // Rendered WITHOUT the location: the position travels structurally on [KotlinCompilerError], and
        // generated-source line numbers in user-facing prose would name a file the author never wrote.
        private fun render(error: ScriptDiagnostic): String {
            return error.render(
                withSeverity = false,
                withLocation = false,
                withException = true,
                withStackTrace = false)
        }


        // Absolute offset of the start of each line of [sourceText], indexed by 0-based line number.
        // A diagnostic's own `absolutePos` is never populated, so the index has to be built here.
        private fun lineStartOffsets(sourceText: String): List<Int> {
            val builder = mutableListOf(0)
            for ((index, character) in sourceText.withIndex()) {
                if (character == '\n') {
                    builder.add(index + 1)
                }
            }
            return builder
        }


        // A diagnostic's `line` and `col` are both 1-based over the source text exactly as handed to the
        // compiler. The top of the region is INCLUSIVE because a parse error points one past the end of its
        // line — for a one-line expression that is the offset just past the last character.
        private fun userCodeOffset(
            error: ScriptDiagnostic,
            lineStartOffsets: List<Int>,
            userCodeRegion: KotlinCode.UserCodeRegion
        ): Int? {
            val start = error.location?.start
                ?: return null

            val lineIndex = start.line - 1
            if (lineIndex !in lineStartOffsets.indices) {
                return null
            }

            val absoluteOffset = lineStartOffsets[lineIndex] + (start.col - 1)

            return (absoluteOffset - userCodeRegion.offset)
                .takeIf { it in 0..userCodeRegion.length }
        }
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

        return when {
            errors.isEmpty() ->
                KotlinCompilerSuccess(outputJarFile, classNamePrefix)

            else ->
                selectError(errors, kotlinCode)
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