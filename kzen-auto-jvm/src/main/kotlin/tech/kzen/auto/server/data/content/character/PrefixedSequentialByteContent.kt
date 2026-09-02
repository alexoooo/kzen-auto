package tech.kzen.auto.server.data.content.character

import tech.kzen.auto.server.data.content.SequentialByteContent


class PrefixedSequentialByteContent(
    prefix: ByteArray,
    private val inner: SequentialByteContent
): SequentialByteContent {
    private val prefix = prefix.copyOf()
    private var prefixOffset = 0
    private var closed = false


    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        require(offset >= 0 && length >= 0 && offset + length <= buffer.size)
        if (length == 0) return 0
        if (prefixOffset < prefix.size) {
            val count = minOf(length, prefix.size - prefixOffset)
            prefix.copyInto(buffer, offset, prefixOffset, prefixOffset + count)
            prefixOffset += count
            return count
        }
        return inner.read(buffer, offset, length)
    }


    override fun close() {
        if (closed) return
        closed = true
        inner.close()
    }
}
