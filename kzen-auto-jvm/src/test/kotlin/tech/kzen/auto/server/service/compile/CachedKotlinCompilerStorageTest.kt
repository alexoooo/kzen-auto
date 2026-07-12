package tech.kzen.auto.server.service.compile

import tech.kzen.auto.server.service.storage.StorageLruEvictor
import tech.kzen.auto.server.util.WorkUtils
import java.lang.classfile.ClassFile
import java.lang.constant.ClassDesc
import java.lang.constant.ConstantDescs
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue


/**
 * Covers the storage coordination of [CachedKotlinCompiler]: a loaded entry's jar must be physically
 * deletable (the loader's jar file handle blocks deletion on Windows until closed), already-handed-out
 * classes must survive the deletion, and LRU eviction must respect budget and recency.
 *
 * Uses a fake [KotlinCompiler] that assembles a jar of empty classes with the JDK ClassFile API, so no
 * real Kotlin compilation runs.
 */
class CachedKotlinCompilerStorageTest {
    //-----------------------------------------------------------------------------------------------------------------
    private class JarWritingFakeCompiler(
        private val padBytes: Int = 0
    ): KotlinCompiler {
        var compileCount = 0

        override fun compile(
            kotlinCode: KotlinCode,
            outputJarFile: Path,
            classpathLocations: List<Path>,
            classLoader: ClassLoader
        ): KotlinCompilerResult {
            compileCount++
            Files.createDirectories(outputJarFile.parent)

            val mainName = KotlinCode.classNamePrefix + kotlinCode.mainClassName
            val nestedName = "$mainName\$Nested"

            JarOutputStream(Files.newOutputStream(outputJarFile)).use { jar ->
                for (className in listOf(mainName, nestedName)) {
                    jar.putNextEntry(ZipEntry("$className.class"))
                    jar.write(ClassFile.of().build(ClassDesc.of(className)) { classBuilder ->
                        classBuilder.withFlags(ClassFile.ACC_PUBLIC)
                        classBuilder.withSuperclass(ConstantDescs.CD_Object)
                    })
                    jar.closeEntry()
                }

                if (padBytes > 0) {
                    // Random padding stays incompressible, so the jar's on-disk size tracks padBytes.
                    jar.putNextEntry(ZipEntry("padding.bin"))
                    jar.write(Random(42).nextBytes(padBytes))
                    jar.closeEntry()
                }
            }
            return KotlinCompilerSuccess(outputJarFile, KotlinCode.classNamePrefix)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val workUtils = WorkUtils.temporary("cached-kotlin-compiler-storage-test")


    @AfterTest
    fun tearDown() {
        WorkUtils.recursivelyDeleteDir(workUtils.base())
    }


    private fun code(name: String): KotlinCode {
        return KotlinCode(name, "// source of $name")
    }


    private fun successFile(kotlinCode: KotlinCode): Path {
        return workUtils.base()
            .resolve("code-cache")
            .resolve(kotlinCode.signature())
            .resolve("success.txt")
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun deleteEntryReleasesJarLockAndAllowsRecompile() {
        val fakeCompiler = JarWritingFakeCompiler()
        val compiler = CachedKotlinCompiler(fakeCompiler, workUtils)
        val kotlinCode = code("DeleteMe")
        val classLoader = javaClass.classLoader

        assertNull(compiler.tryCompile(kotlinCode, classLoader))
        val loaded = compiler.tryLoad(kotlinCode, classLoader)
        assertNotNull(loaded)

        assertNull(compiler.deleteEntry(kotlinCode.signature()))
        assertFalse(Files.exists(successFile(kotlinCode).parent))

        // The handed-out Class keeps working, including resolution of its eagerly defined nested class.
        assertEquals(KotlinCode.classNamePrefix + "DeleteMe", loaded!!.name)
        val nested = Class.forName("${loaded.name}\$Nested", false, loaded.classLoader)
        assertEquals("${loaded.name}\$Nested", nested.name)

        assertNull(compiler.tryCompile(kotlinCode, classLoader))
        assertEquals(2, fakeCompiler.compileCount)

        val reloaded = compiler.tryLoad(kotlinCode, classLoader)
        assertNotNull(reloaded)
    }


    @Test
    fun tryLoadReturnsNullWhenNeverCompiledAndCachesWhenLoaded() {
        val compiler = CachedKotlinCompiler(JarWritingFakeCompiler(), workUtils)
        val kotlinCode = code("CacheMe")
        val classLoader = javaClass.classLoader

        assertNull(compiler.tryLoad(kotlinCode, classLoader))

        assertNull(compiler.tryCompile(kotlinCode, classLoader))
        val first = compiler.tryLoad(kotlinCode, classLoader)
        val second = compiler.tryLoad(kotlinCode, classLoader)
        assertSame(first, second)
    }


    @Test
    fun cacheHitRefreshesLruSignal() {
        val compiler = CachedKotlinCompiler(JarWritingFakeCompiler(), workUtils)
        val kotlinCode = code("TouchMe")
        val classLoader = javaClass.classLoader

        assertNull(compiler.tryCompile(kotlinCode, classLoader))

        val staleMillis = 1_000_000L
        Files.setLastModifiedTime(successFile(kotlinCode), FileTime.fromMillis(staleMillis))

        assertNull(compiler.tryCompile(kotlinCode, classLoader))

        val refreshed = Files.getLastModifiedTime(successFile(kotlinCode)).toMillis()
        assertTrue(refreshed > staleMillis, "expected refreshed mtime, got $refreshed")
    }


    @Test
    fun evictionDeletesLeastRecentlyUsedToFitBudget() {
        val perEntryPadBytes = 10_000
        val budgetBytes = 25_000L

        val compiler = CachedKotlinCompiler(JarWritingFakeCompiler(perEntryPadBytes), workUtils)
        val area = compiler.storageArea(budgetBytes)
        compiler.attachEvictor(StorageLruEvictor(area))
        val classLoader = javaClass.classLoader

        val oldest = code("Oldest")
        val middle = code("Middle")
        val newest = code("Newest")

        assertNull(compiler.tryCompile(oldest, classLoader))
        assertNull(compiler.tryCompile(middle, classLoader))
        Files.setLastModifiedTime(successFile(oldest), FileTime.fromMillis(1_000_000))
        Files.setLastModifiedTime(successFile(middle), FileTime.fromMillis(2_000_000))

        assertNull(compiler.tryCompile(newest, classLoader))

        val remaining = area.bundles().map { it.key }.toSet()
        assertEquals(
            setOf(middle.signature(), newest.signature()),
            remaining)
        assertTrue(area.bundles().sumOf { it.sizeBytes } <= budgetBytes)
    }
}
