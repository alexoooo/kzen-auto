package tech.kzen.auto.server.objects.job.worker.content.tar

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import tech.kzen.auto.server.data.content.SequentialByteContent
import tech.kzen.auto.server.objects.job.worker.LentElement
import tech.kzen.auto.server.objects.job.worker.content.Content
import tech.kzen.auto.server.objects.job.worker.content.ContentDescriptor
import tech.kzen.auto.server.objects.job.worker.content.ContentLifetime


/**
 * The [ContentLifetime.CursorBorrowed] content of the tar entry the cursor is positioned on. Reads come from
 * the archive stream, which ends at the entry boundary. [release] invalidates (design §6.2): a later open or
 * read fails by name instead of reading the next entry's bytes. Closing the opened handle never closes
 * the archive; it records whether the entry was read to its end ([consumed]) or closed with bytes remaining
 * ([closedEarly]), which the cursor skips before advancing.
 *
 * A lent element (docs/plans/2026-09-16_borrowed-elements.md §3.1), because its lifetime is the cursor's: the
 * run adopts it at the pull, and its [close] — the last hold's release — is the cue to advance.
 */
class TarEntryContent internal constructor(
    private val descriptor: ContentDescriptor,
    private val archiveName: String,
    private val archive: TarArchiveInputStream
): Content, LentElement {
    @Volatile private var opened = false
    @Volatile private var atEnd = false
    @Volatile private var handleClosed = false
    @Volatile private var invalidated = false


    val wasOpened: Boolean get() = opened

    /** Read to the end of the entry before the handle closed. */
    val consumed: Boolean get() = opened && atEnd

    /** Handle closed with bytes remaining. */
    val closedEarly: Boolean get() = opened && handleClosed && !atEnd

    val isInvalidated: Boolean get() = invalidated


    override fun descriptor(): ContentDescriptor = descriptor

    override fun lifetime(): ContentLifetime = ContentLifetime.CursorBorrowed


    override fun open(): SequentialByteContent {
        checkValid()
        check(!opened) { "Entry '${descriptor.name}' of '$archiveName' is cursor-borrowed and was already opened" }
        opened = true
        return Handle()
    }


    /** The release (the entry's last hold, or the cursor advancing): nothing may read this entry any more. */
    override fun release() {
        invalidated = true
    }


    override fun lentName(): String = "entry '${descriptor.name}'"

    override fun lender(): String = "'$archiveName'"


    /** The last hold's release: the cursor may advance. */
    override fun close() {
        release()
    }


    private fun checkValid() {
        check(!invalidated) { "Entry '${descriptor.name}' of '$archiveName' was released; its cursor has advanced" }
    }


    private inner class Handle: SequentialByteContent {
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            require(offset >= 0 && length >= 0 && offset + length <= buffer.size)
            checkValid()
            check(!handleClosed) { "Entry '${descriptor.name}' of '$archiveName' handle is closed" }
            if (length == 0) return 0
            if (atEnd) return -1
            val count = archive.read(buffer, offset, length)
            if (count == -1) {
                atEnd = true
            }
            return count
        }

        override fun close() {
            handleClosed = true
        }
    }
}
