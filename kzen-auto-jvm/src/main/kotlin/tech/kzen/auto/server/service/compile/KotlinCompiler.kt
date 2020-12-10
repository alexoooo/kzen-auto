package tech.kzen.auto.server.service.compile

import java.nio.file.Path


interface KotlinCompiler {
    fun tryCompileModule(
        moduleName: String,
        sourcePaths: List<Path>,
        saveClassesDir: Path,
        classpathLocations: List<Path>,
        classLoader: ClassLoader
    ): String?
}