package tech.kzen.auto.server.service.compile

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CompilerConfiguration


/**
 * The one Kotlin compiler project held open for the life of the JVM, which keeps the compiler's shared
 * application environment alive between compiles.
 *
 * The compiler reference-counts that environment across its projects and tears it down, parsed classpath jar
 * indexes included, the moment the last project is disposed. Every script compile builds and disposes its own
 * isolated project, so without a standing project each compile re-parses the central directory of every
 * classpath jar — about a fifth of its time. Held open, each jar is indexed once per JVM, whether a syntax check
 * or a compile runs first. Compiles stay isolated: each still gets its own project, configuration, and sessions.
 *
 * Built once and never disposed: KotlinCoreEnvironment registers application-level extensions, so a
 * create/dispose cycle costs seconds and risks double-registration across the many KzenAutoContext lifecycles a
 * test run creates.
 */
internal object KotlinApplicationEnvironment {
    // K1-tagged because it predates the K2 frontend; the project is frontend-independent (parsing only)
    @OptIn(K1Deprecation::class, CompilerConfiguration.Internals::class)
    val project: Project by lazy {
        KotlinCoreEnvironment
            .createForProduction(
                Disposer.newDisposable(KotlinApplicationEnvironment::class.java.simpleName),
                CompilerConfiguration(),
                EnvironmentConfigFiles.JVM_CONFIG_FILES)
            .project
    }


    /** Ensures the standing project exists before a compile opens (and later disposes) its own. */
    fun retain() {
        project
    }
}
