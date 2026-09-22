package tech.kzen.auto.server.data.read.archive

import tech.kzen.auto.plugin.api.data.ReaderByteInput
import java.io.InputStream


/** A [ReaderByteInput] as a plain [InputStream]; the input stays owned by whoever opened it, so close is a no-op. */
internal class ReaderByteInputStream(
    private val input: ReaderByteInput
): InputStream() {
    private val single = ByteArray(1)


    override fun read(): Int {
        val count = read(single, 0, 1)
        return if (count <= 0) -1 else single[0].toInt() and 0xff
    }


    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) {
            return 0
        }
        return input.read(buffer, offset, length)
    }
}
