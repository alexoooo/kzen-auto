package tech.kzen.auto.server.data.content.local

import tech.kzen.auto.server.data.content.SequentialByteContent
import tech.kzen.auto.server.data.content.policy.ContentReadControl
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel


class ChannelSequentialByteContent(
    private val channel: SeekableByteChannel,
    private val control: ContentReadControl
): SequentialByteContent {
    private var closed = false


    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        require(offset >= 0 && length >= 0 && offset + length <= buffer.size)
        if (length == 0) return 0
        control.checkpoint()
        val count = channel.read(ByteBuffer.wrap(buffer, offset, length))
        control.checkpoint()
        return count
    }


    override fun close() {
        if (closed) return
        closed = true
        channel.close()
    }
}
