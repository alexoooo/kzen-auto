package tech.kzen.auto.server.objects.job.worker.content.tar

import com.linkedin.migz.MiGzOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import tech.kzen.auto.server.objects.job.worker.content.ContentDescriptor
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlin.test.Test
import kotlin.test.assertEquals


class TarEntryCursorTest {
    @Test
    fun anArchiveCompressedAsManyGzipMembersYieldsEveryEntryWhole() {
        val entries = (0 until 3).map { index ->
            "entry-$index.txt" to (0 until 2_000).joinToString("\n") { "$index:$it" }.encodeToByteArray()
        }

        // A parallel compressor (Write's MiGz) writes a gzip member per block; the entries span many.
        val compressed = ByteArrayOutputStream()
        MiGzOutputStream(compressed as OutputStream, 3, 1000).use { it.write(tar(entries)) }

        assertEquals(
            entries.map { (name, bytes) -> name to bytes.decodeToString() },
            read("input.tar.gz", compressed.toByteArray()))
    }


    @Test
    fun aPlainTarNameIsReadWithoutInflating() {
        val entries = listOf("a.txt" to "alpha".encodeToByteArray(), "b.txt" to "bravo".encodeToByteArray())

        assertEquals(
            entries.map { (name, bytes) -> name to bytes.decodeToString() },
            read("input.tar", tar(entries)))
    }


    private fun tar(entries: List<Pair<String, ByteArray>>): ByteArray {
        val tar = ByteArrayOutputStream()
        TarArchiveOutputStream(tar).use { output ->
            for ((name, bytes) in entries) {
                val entry = TarArchiveEntry(name)
                entry.size = bytes.size.toLong()
                output.putArchiveEntry(entry)
                output.write(bytes)
                output.closeArchiveEntry()
            }
        }
        return tar.toByteArray()
    }


    private fun read(archiveName: String, archive: ByteArray): List<Pair<String, String>> {
        val descriptor = ContentDescriptor(archiveName, archive.size.toLong(), null)
        return TarEntryCursor(descriptor, ByteArrayInputStream(archive)).use { cursor ->
            cursor.asSequence().map { content ->
                content.descriptor().name to content.open().use { input ->
                    val bytes = ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    while (true) {
                        val count = input.read(buffer, 0, buffer.size)
                        if (count == -1) break
                        bytes.write(buffer, 0, count)
                    }
                    bytes.toByteArray().decodeToString()
                }
            }.toList()
        }
    }
}
