package tech.kzen.auto.server.data.content.coding

import tech.kzen.auto.server.data.content.ContentCodingException
import tech.kzen.auto.server.data.content.SequentialByteContent
import tech.kzen.auto.server.data.content.policy.ContentReadControl


class ExpandedByteLimitContent(
    private val inner: SequentialByteContent,
    private val control: ContentReadControl,
    private val source: String,
    private val part: String?
): SequentialByteContent {
    private var emitted = 0L
    private var closed = false


    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        control.checkpoint()
        val count = inner.read(buffer, offset, length)
        if (count > 0) {
            if (count.toLong() > control.maximumExpandedBytes - emitted) {
                throw ContentCodingException(
                    source, part, emitted,
                    "Expanded byte limit ${control.maximumExpandedBytes} exceeded")
            }
            emitted += count
            control.recordExpandedBytes(count)
        }
        control.checkpoint()
        return count
    }


    override fun close() {
        if (closed) return
        closed = true
        inner.close()
    }
}
