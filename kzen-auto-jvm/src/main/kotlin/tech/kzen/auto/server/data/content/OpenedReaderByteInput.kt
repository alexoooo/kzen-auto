package tech.kzen.auto.server.data.content

import tech.kzen.auto.plugin.api.data.ReaderByteInput
import tech.kzen.auto.common.data.read.DataContentFingerprint
import tech.kzen.auto.server.data.content.policy.ContentReadControl


class OpenedReaderByteInput internal constructor(
    private val bytes: SequentialByteContent,
    private val control: ContentReadControl,
    val observedFingerprint: DataContentFingerprint
): ReaderByteInput, AutoCloseable {
    /** The source bytes read so far, beneath any coding: the read's progress through the stored content. */
    val sourceBytesRead: Long get() = control.sourceBytesRead

    private var closed = false


    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        check(!closed) { "Reader byte input is closed" }
        try {
            return bytes.read(buffer, offset, length)
        }
        catch (failure: Throwable) {
            try {
                close()
            }
            catch (closeFailure: Throwable) {
                failure.addSuppressed(closeFailure)
            }
            throw failure
        }
    }


    override fun close() {
        if (closed) return
        closed = true
        bytes.close()
    }
}
