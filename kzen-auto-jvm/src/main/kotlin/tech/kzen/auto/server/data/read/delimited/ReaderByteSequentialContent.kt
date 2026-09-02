package tech.kzen.auto.server.data.read.delimited

import tech.kzen.auto.plugin.api.data.ReaderByteInput
import tech.kzen.auto.server.data.content.SequentialByteContent


class ReaderByteSequentialContent(
    private val input: ReaderByteInput
): SequentialByteContent {
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        input.read(buffer, offset, length)

    override fun close() = Unit
}
