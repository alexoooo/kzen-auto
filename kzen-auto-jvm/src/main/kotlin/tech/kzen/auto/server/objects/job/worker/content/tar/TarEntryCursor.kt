package tech.kzen.auto.server.objects.job.worker.content.tar

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import tech.kzen.auto.common.data.format.FileFormatNames
import tech.kzen.auto.common.data.read.ContentCodingSpec
import tech.kzen.auto.server.objects.job.worker.content.ContentDescriptor
import java.io.BufferedInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path


/**
 * Streams the file entries of a tar archive in archive order, one member's [TarEntryContent] at a time. The archive's
 * name decides whether it is gzip-compressed ([FileFormatNames.contentCoding]: `.tar.gz` or `.tgz`, against a plain
 * `.tar`); its bytes are never inspected for that. The content's descriptor
 * carries the header's name, size and modification time. Only file entries are produced: directories, links
 * and other special entries are passed over. Advancing invalidates the previous entry's content and lets the
 * archive stream skip whatever of it was not read; closing the cursor closes the archive. The caller advances
 * only after the previous entry is released (the lending Worker's responsibility, not this class's). The archive
 * bytes come from a file ([Path]) or from any stream — an entry of an enclosing archive, for nested extraction
 * — described by [parent].
 */
class TarEntryCursor(
    private val parent: ContentDescriptor,
    bytes: InputStream
): Iterator<TarEntryContent>, AutoCloseable {
    constructor(path: Path): this(
        ContentDescriptor(path.fileName.toString(), Files.size(path), Files.getLastModifiedTime(path).toMillis()),
        Files.newInputStream(path))

    companion object {
        private const val readBufferBytes = 64 * 1024

        /** Test seam: sees every cursor as it opens, for close and open counting (spike verification); null in
         *  production. */
        @Volatile
        internal var observer: ((TarEntryCursor) -> Unit)? = null
    }

    private val archiveName = parent.name
    private val archive = TarArchiveInputStream(decoded(BufferedInputStream(bytes, readBufferBytes)))

    private var pending: TarArchiveEntry? = null
    private var current: TarEntryContent? = null
    private var exhausted = false
    private var closed = false
    private var closeCount = 0

    /** Every entry content handed out, in order (test observation). */
    internal val produced = ArrayList<TarEntryContent>()

    init {
        observer?.invoke(this)
    }


    private fun decoded(buffered: InputStream): InputStream {
        return when (FileFormatNames.contentCoding(archiveName)) {
            // Every member of a multi-member gzip (a parallel compressor writes many), not only the first.
            ContentCodingSpec.gzip -> GzipCompressorInputStream(buffered, true)
            else -> buffered
        }
    }


    val archiveFileName: String get() = archiveName

    val isClosed: Boolean get() = closed

    fun closeCount(): Int = closeCount


    override fun hasNext(): Boolean {
        check(!closed) { "Cursor over '$archiveName' is closed" }
        if (pending != null) return true
        if (exhausted) return false
        current?.release()
        current = null
        while (true) {
            val header = archive.nextEntry
            if (header == null) {
                exhausted = true
                return false
            }
            if (header.isFile) {
                pending = header
                return true
            }
        }
    }


    override fun next(): TarEntryContent {
        if (!hasNext()) throw NoSuchElementException()
        val header = pending!!
        pending = null
        val descriptor = ContentDescriptor(header.name, header.size, header.lastModifiedDate?.time)
        val content = TarEntryContent(descriptor, archiveName, archive)
        current = content
        produced.add(content)
        return content
    }


    override fun close() {
        closeCount += 1
        if (closed) return
        closed = true
        current?.release()
        archive.close()
    }
}
