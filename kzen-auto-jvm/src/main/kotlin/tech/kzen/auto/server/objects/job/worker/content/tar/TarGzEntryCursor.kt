package tech.kzen.auto.server.objects.job.worker.content.tar

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import tech.kzen.auto.server.objects.job.worker.content.ContentDescriptor
import java.io.BufferedInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path


/**
 * Streams the file entries of a `.tar.gz` in archive order, one member's [TarEntryContent] at a time (spike: tar
 * over gzip is hard-wired; the design's negotiated container access is not built). The content's descriptor
 * carries the header's name, size and modification time. Selection runs on the header, before an element
 * exists, so a non-matching entry costs one skip. Advancing invalidates the previous entry's content and lets the
 * archive stream skip whatever of it was not read; closing the cursor closes the archive. The caller advances
 * only after the previous entry is released (the lending Worker's responsibility, not this class's). The archive
 * bytes come from a file ([Path]) or from any stream — an entry of an enclosing archive, for nested extraction
 * — described by [parent].
 */
class TarGzEntryCursor(
    private val parent: ContentDescriptor,
    bytes: InputStream,
    select: (String) -> Boolean
): Iterator<TarEntryContent>, AutoCloseable {
    constructor(path: Path, select: (String) -> Boolean): this(
        ContentDescriptor(path.fileName.toString(), Files.size(path), Files.getLastModifiedTime(path).toMillis()),
        Files.newInputStream(path),
        select)

    companion object {
        private const val readBufferBytes = 64 * 1024

        /** Test seam: sees every cursor as it opens, for close and open counting (spike verification); null in
         *  production. */
        @Volatile
        internal var observer: ((TarGzEntryCursor) -> Unit)? = null
    }

    private val archiveName = parent.name
    private val archive = TarArchiveInputStream(
        GzipCompressorInputStream(BufferedInputStream(bytes, readBufferBytes)))

    private var pending: TarArchiveEntry? = null
    private var current: TarEntryContent? = null
    private var exhausted = false
    private var skippedCount = 0L
    private var closed = false
    private var closeCount = 0
    private var select = select

    /** Every entry content handed out, in order (test observation). */
    internal val produced = ArrayList<TarEntryContent>()

    init {
        observer?.invoke(this)
    }


    val archiveFileName: String get() = archiveName

    val isClosed: Boolean get() = closed

    fun closeCount(): Int = closeCount


    /** Header-selected entries that were skipped without producing an element. */
    fun skipped(): Long = skippedCount


    /**
     * Replaces the header selection from the next header on (a live edit of `entries:`, spike CS3): the cursor
     * never rewinds, so an entry already selected or skipped stays so. Between entries no header is pending.
     */
    fun reselect(select: (String) -> Boolean) {
        this.select = select
    }


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
            if (header.isFile && select(header.name)) {
                pending = header
                return true
            }
            skippedCount += 1
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
