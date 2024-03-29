package tech.kzen.auto.server.service.compile

import java.nio.file.Path


interface KotlinCompiler {
    /**
     * @return error message, or null if successful
     */
    fun compile(
        kotlinCode: KotlinCode,
        outputJarFile: Path,
        classpathLocations: List<Path>,
        classLoader: ClassLoader
    ): KotlinCompilerResult
}