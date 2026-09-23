package tech.kzen.auto.server.data

import java.nio.file.Files
import java.nio.file.attribute.FileTime
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals


class CodeFingerprintTest {
    @Test
    fun aRewrittenJarChangesItsFingerprint() {
        val jar = Files.createTempFile("code-fingerprint", ".jar")
        try {
            jar.writeText("v1")
            Files.setLastModifiedTime(jar, FileTime.fromMillis(1_000_000))
            val original = CodeFingerprint.fingerprint(jar)
            assertEquals(original, CodeFingerprint.fingerprint(jar), "an unchanged jar keeps its fingerprint")

            Files.setLastModifiedTime(jar, FileTime.fromMillis(2_000_000))
            assertNotEquals(original, CodeFingerprint.fingerprint(jar), "a rebuilt jar of the same size")
        }
        finally {
            Files.deleteIfExists(jar)
        }
    }


    @Test
    fun aRecompiledClassDirectoryChangesItsFingerprint() {
        val classes = Files.createTempDirectory("code-fingerprint-classes")
        try {
            val first = classes.resolve("tech/Reader.class")
            Files.createDirectories(first.parent)
            first.writeText("v1")
            Files.setLastModifiedTime(first, FileTime.fromMillis(1_000_000))
            val original = CodeFingerprint.fingerprint(classes)
            assertEquals(original, CodeFingerprint.fingerprint(classes))

            Files.setLastModifiedTime(first, FileTime.fromMillis(2_000_000))
            val recompiled = CodeFingerprint.fingerprint(classes)
            assertNotEquals(original, recompiled, "an incremental compile rewrites the changed class")

            val added = classes.resolve("tech/Helper.class")
            added.writeText("v1")
            Files.setLastModifiedTime(added, FileTime.fromMillis(1_000_000))
            assertNotEquals(recompiled, CodeFingerprint.fingerprint(classes), "a new class with an older timestamp")
        }
        finally {
            classes.toFile().deleteRecursively()
        }
    }
}
