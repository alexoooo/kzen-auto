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


class TarGzEntryCursorTest {
    @Test
    fun anArchiveCompressedAsManyGzipMembersYieldsEveryEntryWhole() {
        val entries = (0 until 3).map { index ->
            "entry-$index.txt" to (0 until 2_000).joinToString("\n") { "$index:$it" }.encodeToByteArray()
        }

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

        // A parallel compressor (Write's MiGz) writes a gzip member per block; the entries span many.
        val compressed = ByteArrayOutputStream()
        MiGzOutputStream(compressed as OutputStream, 3, 1000).use { it.write(tar.toByteArray()) }

        val descriptor = ContentDescriptor("input.tar.gz", compressed.size().toLong(), null)
        val read = TarGzEntryCursor(descriptor, ByteArrayInputStream(compressed.toByteArray())).use { cursor ->
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

        assertEquals(entries.map { (name, bytes) -> name to bytes.decodeToString() }, read)
    }
}
