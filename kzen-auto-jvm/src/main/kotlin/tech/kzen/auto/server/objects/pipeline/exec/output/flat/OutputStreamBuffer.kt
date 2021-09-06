package tech.kzen.auto.server.objects.pipeline.exec.output.flat

import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.*


// based on ByteArrayOutputStream, but un-synchronized and writes to RandomAccessFile
class OutputStreamBuffer: OutputStream() {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val maxArrayLength = Int.MAX_VALUE - 8

        fun newLength(oldLength: Int, minGrowth: Int, prefGrowth: Int): Int {
            val newLength = minGrowth.coerceAtLeast(prefGrowth) + oldLength
            check(newLength - maxArrayLength <= 0)
            return newLength
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var buf: ByteArray = ByteArray(32)
    private var count = 0


    //-----------------------------------------------------------------------------------------------------------------
    override fun write(b: Int) {
        ensureCapacity(count + 1)
        buf[count] = b.toByte()
        count += 1
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        Objects.checkFromIndexSize(off, len, b.size)
        ensureCapacity(count + len)
        System.arraycopy(b, off, buf, count, len)
        count += len
    }


    private fun ensureCapacity(minCapacity: Int) {
        // overflow-conscious code
        val oldCapacity = buf.size
        val minGrowth = minCapacity - oldCapacity
        if (minGrowth > 0) {
            buf = buf.copyOf(
                newLength(oldCapacity, minGrowth, oldCapacity)
            )
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun flushTo(out: OutputStream): Int {
        out.write(buf, 0, count)
        val wrote = count
        reset()
        return wrote
    }


    fun flushTo(out: RandomAccessFile): Int {
        out.write(buf, 0, count)
        val wrote = count
        reset()
        return wrote
    }


    fun reset() {
        count = 0
    }


    fun toByteArray(): ByteArray? {
        return buf.copyOf(count)
    }


    fun size(): Int {
        return count
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun toString(): String {
        return String(buf, 0, count, StandardCharsets.UTF_8)
    }


    override fun close() {}
}