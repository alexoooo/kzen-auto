package tech.kzen.auto.server.service.storage

import tech.kzen.auto.server.util.WorkUtils
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue


class DirectoryStorageAreaTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val root: Path = createTempDirectory("directory-storage-area-test")


    @AfterTest
    fun tearDown() {
        WorkUtils.recursivelyDeleteDir(root)
    }


    private fun area(activeKeys: Set<String> = setOf()): DirectoryStorageArea {
        return object: DirectoryStorageArea(
            "test", "Test", "Test area", root
        ) {
            override fun bundleActive(bundleKey: String): Boolean {
                return bundleKey in activeKeys
            }
        }
    }


    private fun writeBundle(key: String, vararg files: Pair<String, Int>): Path {
        val bundleDir = root.resolve(key)
        Files.createDirectories(bundleDir)
        for ((name, size) in files) {
            Files.write(bundleDir.resolve(name), ByteArray(size))
        }
        return bundleDir
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun missingRootEnumeratesEmpty() {
        val missing = DirectoryStorageArea(
            "missing", "Missing", "Never created", root.resolve("does-not-exist"))

        assertEquals(listOf(), missing.bundles())
    }


    @Test
    fun bundlesReportRecursiveSize() {
        writeBundle("a", "one.bin" to 100, "two.bin" to 50)
        val nested = writeBundle("b", "three.bin" to 7)
        Files.createDirectories(nested.resolve("sub"))
        Files.write(nested.resolve("sub/four.bin"), ByteArray(3))

        val bundles = area().bundles().associateBy { it.key }

        assertEquals(2, bundles.size)
        assertEquals(150, bundles["a"]!!.sizeBytes)
        assertEquals(10, bundles["b"]!!.sizeBytes)
        assertFalse(bundles["a"]!!.active)
    }


    @Test
    fun lastModifiedIsMaxWithinBundle() {
        val bundleDir = writeBundle("a", "old.bin" to 1, "new.bin" to 1)
        val oldTime = FileTime.fromMillis(1_000_000)
        val newTime = FileTime.fromMillis(2_000_000)
        Files.setLastModifiedTime(bundleDir.resolve("old.bin"), oldTime)
        Files.setLastModifiedTime(bundleDir.resolve("new.bin"), newTime)
        Files.setLastModifiedTime(bundleDir, oldTime)

        val bundle = area().bundles().single()

        assertEquals(2_000_000, bundle.lastModifiedMillis)
    }


    @Test
    fun deleteRemovesBundleDir() {
        writeBundle("a", "one.bin" to 100)

        val error = area().deleteBundle("a")

        assertNull(error)
        assertFalse(Files.exists(root.resolve("a")))
    }


    @Test
    fun deleteRefusesActiveBundle() {
        writeBundle("a", "one.bin" to 100)

        val error = area(activeKeys = setOf("a")).deleteBundle("a")

        assertNotNull(error)
        assertTrue(Files.exists(root.resolve("a")))
    }


    @Test
    fun deleteRefusesUnknownAndEscapingKeys() {
        writeBundle("a", "one.bin" to 100)
        val sibling = Files.createDirectories(root.parent.resolve("dsa-test-sibling"))
        try {
            assertNotNull(area().deleteBundle("no-such-bundle"))
            assertNotNull(area().deleteBundle("../" + sibling.fileName))
            assertNotNull(area().deleteBundle(".."))
            assertTrue(Files.exists(sibling))
        }
        finally {
            Files.deleteIfExists(sibling)
        }
    }


    @Test
    fun deleteRefusedOnNonDeletableArea() {
        writeBundle("a", "one.bin" to 100)
        val displayOnly = DirectoryStorageArea(
            "test", "Test", "Display only", root, deletable = false)

        assertNotNull(displayOnly.deleteBundle("a"))
        assertTrue(Files.exists(root.resolve("a")))
    }
}
