package tech.kzen.auto.server.service.compile

import java.nio.file.Path


interface KotlinCompiler {
    /**
     * @return error message, or null if successful
     */
    fun tryCompileModule(
        moduleName: String,
        sourcePaths: List<Path>,
        saveClassesDir: Path,
        classpathLocations: List<Path>,
        classLoader: ClassLoader
    ): String?
}