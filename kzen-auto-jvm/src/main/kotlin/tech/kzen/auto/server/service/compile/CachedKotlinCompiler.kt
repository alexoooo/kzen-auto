package tech.kzen.auto.server.service.compile

import tech.kzen.auto.server.objects.report.service.ReportWorkPool
import tech.kzen.auto.server.util.WorkUtils
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime


class CachedKotlinCompiler(
    private val kotlinCompiler: KotlinCompiler,
    workUtils: WorkUtils
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val codeCacheDir = "code-cache"
        private const val jarExtension = ".jar"
//        private const val sourceExtension = ".kt"
        private const val errorFile = "err.txt"
        private const val successFile = "success.txt"
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val cacheDir = workUtils.resolve(codeCacheDir)


    //-----------------------------------------------------------------------------------------------------------------
    init {
        Files.createDirectories(cacheDir)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun errorFile(codeDir: Path): Path {
        return codeDir.resolve(errorFile)
    }


    private fun writeErrorFile(codeDir: Path, errorMessage: String) {
        Files.write(errorFile(codeDir), errorMessage.toByteArray())
    }


    private fun readErrorFile(codeDir: Path): String? {
        val errorFile = errorFile(codeDir)

        if (! Files.exists(errorFile)) {
            return null
        }

        return Files.readString(errorFile, Charsets.UTF_8)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun successFile(codeDir: Path): Path {
        return codeDir.resolve(successFile)
    }


    private fun writeSuccessFile(codeDir: Path) {
        Files.write(successFile(codeDir), "${LocalDateTime.now()}".toByteArray())
    }


    private fun hasSuccess(codeDir: Path): Boolean {
        return Files.exists(successFile(codeDir))
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun tryLoad(
        kotlinCode: KotlinCode,
        classLoader: ClassLoader
    ): Class<out Any>? {
        val signature = kotlinCode.signature()
        val codeDir = cacheDir.resolve(signature)
        val outputJar = outputJar(codeDir, kotlinCode.mainClassName)

//        val classDir = codeDir.resolve(buildDir)
//        val classUrl = classDir.toUri().toURL()

        val sourceClassLoader = URLClassLoader(
            arrayOf(outputJar.toUri().toURL()),
            classLoader)

        @Suppress("LiftReturnOrAssignment")
        try {
            val fullClassName = KotlinCode.classNamePrefix + kotlinCode.mainClassName
            return sourceClassLoader.loadClass(fullClassName)
        }
        catch (e: ClassNotFoundException) {
            return null
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun tryCompile(
        kotlinCode: KotlinCode,
        classLoader: ClassLoader
    ): String? {
        val codeDir = cacheDir.resolve(kotlinCode.signature())

        val previouslyCompiled = Files.exists(codeDir)
        if (! previouslyCompiled) {
            return tryCompileNew(kotlinCode, codeDir, classLoader)
        }

        val previousError = readErrorFile(codeDir)
        if (previousError != null) {
            return previousError
        }

        if (hasSuccess(codeDir)) {
            return null
        }

        ReportWorkPool.deleteDir(codeDir)
        return tryCompileNew(kotlinCode, codeDir, classLoader)
    }


    private fun tryCompileNew(
        kotlinCode: KotlinCode,
        codeDir: Path,
        classLoader: ClassLoader
    ): String? {
        val outputJar = outputJar(codeDir, kotlinCode.mainClassName)

        val result = kotlinCompiler.compile(
            kotlinCode,
            outputJar,
            listOf(),
            classLoader)

        if (result is KotlinCompilerSuccess){
            writeSuccessFile(codeDir)
            return null
        }
        result as KotlinCompilerError
        writeErrorFile(codeDir, result.error)
        return result.error
    }


    private fun outputJar(codeDir: Path, mainClassName: String): Path {
        return codeDir
            .resolve(mainClassName + jarExtension)
            .toAbsolutePath()
            .normalize()
    }
}