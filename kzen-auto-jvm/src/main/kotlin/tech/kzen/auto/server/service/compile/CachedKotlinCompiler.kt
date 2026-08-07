package tech.kzen.auto.server.service.compile

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.google.common.util.concurrent.Striped
import tech.kzen.auto.server.service.storage.DirectoryStorageArea
import tech.kzen.auto.server.service.storage.ManagedStorageArea
import tech.kzen.auto.server.service.storage.StorageLruEvictor
import tech.kzen.auto.server.util.WorkUtils
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean
import java.util.jar.JarFile


class CachedKotlinCompiler(
    private val kotlinCompiler: KotlinCompiler,
    workUtils: WorkUtils
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val codeCacheDir = "code-cache"
        private const val jarExtension = ".jar"
        private const val classFileExtension = ".class"
        private const val errorFile = "err.txt"
        private const val successFile = "success.txt"

        // Optional FIRST line of [errorFile], written only when the error carries a position: the rest of the
        // file is the message verbatim. A header line rather than a suffix or a sidecar because compiler
        // messages are themselves multi-line, so only the first line can be safely reserved. An entry without
        // it decodes as "no position", which is what keeps every already-written cache entry usable — this is
        // a user work directory, and invalidating it would silently recompile everything.
        private const val errorOffsetHeaderPrefix = "#kzen-offset:"

        // Upper bound on hot loaded expression classes held in memory (see [loadedClasses]). The working set of
        // distinct expressions live at once is the target; over a long-lived process this stays far below the
        // total distinct expressions ever compiled.
        private const val loadedClassCacheSize = 10_000L

        // Number of monitors striping [compileLocks]; fixed so the lock table does not grow per distinct signature.
        private const val compileLockStripes = 64
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Pairs a loaded main class with the URLClassLoader owning its jar file handle, so eviction and deletion
    // can release the handle (a held handle blocks jar deletion on Windows). Close is idempotent: both the
    // synchronous delete path and Caffeine's async removal listener call it.
    private class LoadedCode(
        val clazz: Class<out Any>,
        private val loader: URLClassLoader
    ) {
        private val closed = AtomicBoolean()

        fun close() {
            if (closed.compareAndSet(false, true)) {
                loader.close()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val cacheDir = workUtils.resolve(codeCacheDir)

    // Hot loaded expression classes, keyed by content signature (className + source digest, so an entry can
    // never be stale; the classloader parent is process-stable, so the signature alone is a sufficient key).
    // Each retained Class pins its own URLClassLoader in Metaspace, so this is a bounded (size-capped) cache
    // rather than an unbounded map: a long-lived process compiles far more distinct expressions over its
    // lifetime than are live at once, and an evicted entry is transparently rebuilt from the durable on-disk
    // jar on next request. Removal closes the loader, releasing the jar file handle.
    private val loadedClasses: Cache<String, LoadedCode> = Caffeine.newBuilder()
        .maximumSize(loadedClassCacheSize)
        .removalListener<String, LoadedCode> { _, value, _ -> value?.close() }
        .build()

    // Fixed set of monitors guarding every disk mutation of a signature dir (compile, load, delete, evict), so
    // concurrent compiles of the same source don't race a half-written jar, and a delete can't pull a jar out
    // from under an in-progress load. Striped rather than one-monitor-per-signature so the table stays bounded;
    // distinct signatures may share a stripe, which only adds harmless serialization.
    private val compileLocks = Striped.lock(compileLockStripes)

    @Volatile
    private var evictor: StorageLruEvictor? = null


    //-----------------------------------------------------------------------------------------------------------------
    init {
        Files.createDirectories(cacheDir)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun errorFile(codeDir: Path): Path {
        return codeDir.resolve(errorFile)
    }


    private fun writeErrorFile(codeDir: Path, error: KotlinCompilerError) {
        val content = when (val userCodeOffset = error.userCodeOffset) {
            null -> error.error
            else -> "$errorOffsetHeaderPrefix$userCodeOffset\n${error.error}"
        }
        Files.write(errorFile(codeDir), content.toByteArray())
    }


    private fun readErrorFile(codeDir: Path): KotlinCompilerError? {
        val errorFile = errorFile(codeDir)

        if (!Files.exists(errorFile)) {
            return null
        }

        val content = Files.readString(errorFile, Charsets.UTF_8)

        if (!content.startsWith(errorOffsetHeaderPrefix)) {
            return KotlinCompilerError(content)
        }

        val headerEndIndex = content.indexOf('\n')
        if (headerEndIndex == -1) {
            return KotlinCompilerError(content)
        }

        // Only a well-formed header is consumed; anything else is a message that merely happens to start with
        // the prefix, and must survive intact.
        val userCodeOffset = content
            .substring(errorOffsetHeaderPrefix.length, headerEndIndex)
            .toIntOrNull()
            ?: return KotlinCompilerError(content)

        return KotlinCompilerError(content.substring(headerEndIndex + 1), userCodeOffset)
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
            return it.clazz
        }

        val lock = compileLocks.get(signature)
        lock.lock()
        try {
            loadedClasses.getIfPresent(signature)?.let {
                return it.clazz
            }

            val codeDir = cacheDir.resolve(signature)
            val outputJar = outputJar(codeDir, kotlinCode.mainClassName)

            val sourceClassLoader = URLClassLoader(
                arrayOf(outputJar.toUri().toURL()),
                classLoader)

            val fullClassName = KotlinCode.classNamePrefix + kotlinCode.mainClassName

            var loaded: Class<out Any>? = null
            try {
                val mainClass = sourceClassLoader.loadClass(fullClassName)
                defineJarClasses(outputJar, sourceClassLoader)
                loadedClasses.put(signature, LoadedCode(mainClass, sourceClassLoader))
                loaded = mainClass
            }
            catch (e: ClassNotFoundException) {
                return null
            }
            finally {
                if (loaded == null) {
                    sourceClassLoader.close()
                }
            }
            return loaded
        }
        finally {
            lock.unlock()
        }
    }


    // Defines every class in the jar up front (without static initialization), so a later loader close —
    // which releases the jar file handle for deletion — can't break in-flight code on a lazy load of a
    // nested or lambda class.
    private fun defineJarClasses(outputJar: Path, classLoader: ClassLoader) {
        JarFile(outputJar.toFile()).use { jar ->
            for (entry in jar.entries()) {
                if (!entry.name.endsWith(classFileExtension)) {
                    continue
                }
                val binaryName = entry.name
                    .removeSuffix(classFileExtension)
                    .replace('/', '.')
                Class.forName(binaryName, false, classLoader)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun tryCompile(
        kotlinCode: KotlinCode,
        classLoader: ClassLoader
    ): KotlinCompilerError? {
        val signature = kotlinCode.signature()

        val lock = compileLocks.get(signature)

        var compiledNew = false
        val error: KotlinCompilerError?

        lock.lock()
        try {
            val codeDir = cacheDir.resolve(signature)

            val previouslyCompiled = Files.exists(codeDir)
            if (!previouslyCompiled) {
                error = tryCompileNew(kotlinCode, codeDir, classLoader)
                compiledNew = error == null
            }
            else {
                val previousError = readErrorFile(codeDir)
                if (previousError != null) {
                    error = previousError
                }
                else if (hasSuccess(codeDir)) {
                    // Refresh the LRU signal so a hot entry isn't the next eviction candidate.
                    Files.setLastModifiedTime(successFile(codeDir), FileTime.from(Instant.now()))
                    error = null
                }
                else {
                    WorkUtils.deleteDirThrowing(codeDir)
                    error = tryCompileNew(kotlinCode, codeDir, classLoader)
                    compiledNew = error == null
                }
            }
        }
        finally {
            lock.unlock()
        }

        if (compiledNew) {
            evictor?.maybeEvict()
        }
        return error
    }


    private fun tryCompileNew(
        kotlinCode: KotlinCode,
        codeDir: Path,
        classLoader: ClassLoader
    ): KotlinCompilerError? {
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

        if (Thread.currentThread().isInterrupted) {
            // A compile cut by cancellation (e.g. a Job run failing elsewhere interrupts an in-flight worker
            // compile) reports arbitrary spurious errors — ClosedByInterruptException, or even bogus resolution
            // failures. Persisting one would poison this signature's DURABLE cache entry, replaying the phantom
            // error on every later compile of the same source. Leave the dir partial (no err/success marker)
            // instead: the next request takes the recompile-partial branch and re-derives the truth.
            return result
        }

        writeErrorFile(codeDir, result)
        return result
    }


    private fun outputJar(codeDir: Path, mainClassName: String): Path {
        return codeDir
            .resolve(mainClassName + jarExtension)
            .toAbsolutePath()
            .normalize()
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * Releases the signature's classloader (if loaded) and deletes its cache dir. The next request for the
     * same source transparently recompiles; a Class already handed out keeps executing (see [defineJarClasses]).
     *
     * @return null on success, human-readable error otherwise
     */
    fun deleteEntry(signature: String): String? {
        val lock = compileLocks.get(signature)
        lock.lock()
        try {
            loadedClasses.asMap().remove(signature)?.close()

            val codeDir = cacheDir.resolve(signature)
            if (Files.exists(codeDir)) {
                WorkUtils.deleteDirThrowing(codeDir)
            }
            return null
        }
        catch (e: Exception) {
            return e.message ?: e.toString()
        }
        finally {
            lock.unlock()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun attachEvictor(evictor: StorageLruEvictor) {
        check(this.evictor == null) { "Evictor already attached" }
        this.evictor = evictor
    }


    fun storageArea(budgetBytes: Long): ManagedStorageArea {
        return object: DirectoryStorageArea(
            "code-cache",
            "Compiled formulas",
            "Kotlin expressions (formula columns, script steps) compiled to jars. Rebuilt on demand if " +
                "deleted; least recently used entries are evicted automatically over the size budget.",
            cacheDir,
            budgetBytes = budgetBytes
        ) {
            override fun bundleLastModified(bundleDir: Path): Long {
                val success = successFile(bundleDir)
                return when {
                    Files.exists(success) ->
                        Files.getLastModifiedTime(success).toMillis()

                    else ->
                        dirLastModified(bundleDir)
                }
            }

            override fun deleteBundle(bundleKey: String): String? {
                resolveBundleDir(bundleKey)
                    ?: return "Unknown bundle: $bundleKey"
                return deleteEntry(bundleKey)
            }
        }
    }
}
