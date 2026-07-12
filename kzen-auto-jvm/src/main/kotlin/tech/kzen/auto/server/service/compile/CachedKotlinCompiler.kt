package tech.kzen.auto.server.service.compile

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.util.concurrent.Striped
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

        // Upper bound on hot loaded expression classes held in memory (see [loadedClasses]). The working set of
        // distinct expressions live at once is the target; over a long-lived process this stays far below the
        // total distinct expressions ever compiled.
        private const val loadedClassCacheSize = 10_000L

        // Number of monitors striping [compileLocks]; fixed so the lock table does not grow per distinct signature.
        private const val compileLockStripes = 64
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val cacheDir = workUtils.resolve(codeCacheDir)

    // Hot loaded expression classes, keyed by content signature (className + source digest, so an entry can
    // never be stale; the classloader parent is process-stable, so the signature alone is a sufficient key).
    // Each retained Class pins its own URLClassLoader in Metaspace, so this is a bounded LRU rather than an
    // unbounded map: a long-lived process compiles far more distinct expressions over its lifetime than are
    // live at once, and an evicted entry is transparently rebuilt from the durable on-disk jar on next request.
    private val loadedClasses: Cache<String, Class<out Any>> = CacheBuilder.newBuilder()
        .maximumSize(loadedClassCacheSize)
        .build()

    // Fixed set of monitors guarding tryCompile's check-then-act against the on-disk cache directory, so two
    // concurrent compiles of the same source don't race (one writing the jar while the other reads a
    // half-written one). Striped rather than one-monitor-per-signature so the table stays bounded; distinct
    // signatures may share a stripe, which only adds harmless serialization.
    private val compileLocks = Striped.lock(compileLockStripes)


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

        if (!Files.exists(errorFile)) {
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

        loadedClasses.getIfPresent(signature)?.let {
            return it
        }

        val codeDir = cacheDir.resolve(signature)
        val outputJar = outputJar(codeDir, kotlinCode.mainClassName)

        val sourceClassLoader = URLClassLoader(
            arrayOf(outputJar.toUri().toURL()),
            classLoader)

        val fullClassName = KotlinCode.classNamePrefix + kotlinCode.mainClassName
        val loaded =
            try {
                sourceClassLoader.loadClass(fullClassName)
            }
            catch (e: ClassNotFoundException) {
                return null
            }

        loadedClasses.put(signature, loaded)
        return loaded
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun tryCompile(
        kotlinCode: KotlinCode,
        classLoader: ClassLoader
    ): String? {
        val signature = kotlinCode.signature()

        val lock = compileLocks.get(signature)
        lock.lock()
        try {
            val codeDir = cacheDir.resolve(signature)

            val previouslyCompiled = Files.exists(codeDir)
            if (!previouslyCompiled) {
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
        finally {
            lock.unlock()
        }
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