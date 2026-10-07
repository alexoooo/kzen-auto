package tech.kzen.auto.server.data.content

import tech.kzen.auto.server.data.content.policy.ContentReadControl


/**
 * Counts the bytes taken from the source itself, beneath any content coding: what a read has consumed of the
 * stored file (compressed bytes for a gzip file), so it compares with the file's size for read progress.
 */
class SourceByteCountingContent(
    private val delegate: SequentialByteContent,
    private val control: ContentReadControl
): SequentialByteContent {
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val count = delegate.read(buffer, offset, length)
        if (count > 0) {
            control.recordSourceBytes(count)
        }
        return count
    }


    override fun close() {
        delegate.close()
    }
}
