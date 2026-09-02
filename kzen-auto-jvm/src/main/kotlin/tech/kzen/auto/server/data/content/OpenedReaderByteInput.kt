package tech.kzen.auto.server.data.content

import tech.kzen.auto.plugin.api.data.ReaderByteInput
import tech.kzen.auto.server.data.content.policy.ContentReadControl


class OpenedReaderByteInput internal constructor(
    private val bytes: SequentialByteContent,
    private val control: ContentReadControl
): ReaderByteInput, AutoCloseable {
    override val expandedBytesRead: Long get() = control.expandedBytesRead

    private var closed = false


    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        check(!closed) { "Reader byte input is closed" }
        control.beginOperation()
        try {
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
        finally {
            control.endOperation()
        }
    }


    override fun close() {
        if (closed) return
        closed = true
        bytes.close()
    }
}
