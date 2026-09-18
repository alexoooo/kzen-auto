package tech.kzen.auto.server.objects.job.worker.content

import tech.kzen.auto.server.data.content.SequentialByteContent
import java.io.InputStream


/**
 * A [SequentialByteContent] as a plain [InputStream], for the archive readers that take one (a nested archive
 * read out of an enclosing archive's entry). Closing the stream closes the content.
 */
internal class SequentialByteContentInputStream(
    private val content: SequentialByteContent
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
        val count = content.read(buffer, offset, length)
        return if (count <= 0) -1 else count
    }


    override fun close() {
        content.close()
    }
}
