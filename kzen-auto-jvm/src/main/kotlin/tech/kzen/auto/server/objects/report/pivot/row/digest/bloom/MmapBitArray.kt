package tech.kzen.auto.server.objects.report.pivot.row.digest.bloom

import com.sangupta.bloomfilter.core.BitArray
import java.io.RandomAccessFile
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel.MapMode
import java.nio.file.Path


class MmapBitArray(
    backingPath: Path,
    private val maxElements: Int
): BitArray
{
    private val numBytes: Int = (maxElements shr 3) + 1

    private var backingFile: RandomAccessFile? = null
    private var buffer: MappedByteBuffer? = null


    init {
        backingFile = RandomAccessFile(backingPath.toFile(), "rw")

        val startLength = backingFile!!.length()
        val requiredLength = numBytes.toLong()
        extendFile(requiredLength, startLength)

        buffer = backingFile!!.channel.map(MapMode.READ_WRITE, 0, requiredLength)
    }


    /**
     * @see BitArray.getBit
     */
    override fun getBit(index: Int): Boolean {
        if (index > maxElements) {
            throw IndexOutOfBoundsException("Index is greater than max elements permitted")
        }
        val pos = index shr 3 // div 8
        val bit = 1 shl (index and 0x7)
        val bite = buffer!![pos]
        return (bite.toInt() and bit) != 0
    }

    /**
     * @see BitArray.setBit
     */
    override fun setBit(index: Int): Boolean {
        if (index > maxElements) {
            throw IndexOutOfBoundsException("Index is greater than max elements permitted")
        }
        val pos = index shr 3 // div 8
        val bit = 1 shl (index and 0x7)
        var bite = buffer!![pos]
        bite = (bite.toInt() or bit).toByte()
        buffer!!.put(pos, bite)
        return true
    }

    /**
     * @see BitArray.clear
     */
    override fun clear() {
        val localBuffer = buffer!!

        val bite: Byte = 0
        for (index in 0 until numBytes) {
            localBuffer.put(index, bite)
        }
    }

    /**
     * @see BitArray.clearBit
     */
    override fun clearBit(index: Int) {
        if (index > maxElements) {
            throw IndexOutOfBoundsException("Index is greater than max elements permitted")
        }
        val pos = index shr 3 // div 8
        var bit = 1 shl (index and 0x7)
        bit = bit.inv()
        var bite = buffer!![pos]
        bite = (bite.toInt() and bit).toByte()
        buffer!!.put(pos, bite)
    }

    /**
     * @see BitArray.setBitIfUnset
     */
    override fun setBitIfUnset(index: Int): Boolean {
        return if (getBit(index)) {
            setBit(index)
        } else false
    }

    /**
     * @see BitArray.or
     */
    override fun or(bitArray: BitArray?) {
        // TODO Auto-generated method stub
    }

    /**
     * @see BitArray.and
     */
    override fun and(bitArray: BitArray?) {
        // TODO Auto-generated method stub
    }

    /**
     * @see BitArray.bitSize
     */
    override fun bitSize(): Int {
        return numBytes
    }


    private fun extendFile(requiredLength: Long, oldLength: Long) {
        val delta = (requiredLength - oldLength).toInt() + 1
        if (delta <= 0) {
            return
        }

        backingFile!!.setLength(requiredLength)
        backingFile!!.seek(oldLength)

        val zeroBuffer = ByteArray(1024)
        var remaining = delta
        while (remaining > zeroBuffer.size) {
            backingFile!!.write(zeroBuffer)
            remaining -= zeroBuffer.size
        }

        if (remaining > 0) {
            backingFile!!.write(zeroBuffer, 0, remaining)
        }
    }


    override fun close() {
        closeDirectBuffer()
        backingFile!!.close()
    }


    private fun closeDirectBuffer() {
        if (! buffer!!.isDirect) {
            return
        }

        // see: https://stackoverflow.com/a/19447758/1941359
        val unsafeClass = try {
            Class.forName("sun.misc.Unsafe")
        } catch (ex: java.lang.Exception) {
            // jdk.internal.misc.Unsafe doesn't yet have an invokeCleaner() method,
            // but that method should be added if sun.misc.Unsafe is removed.
            Class.forName("jdk.internal.misc.Unsafe")
        }

        val clean: Method = unsafeClass.getMethod("invokeCleaner", ByteBuffer::class.java)
        clean.isAccessible = true

        val theUnsafeField: Field = unsafeClass.getDeclaredField("theUnsafe")
        theUnsafeField.isAccessible = true

        val theUnsafe: Any = theUnsafeField.get(null)
        clean.invoke(theUnsafe, buffer!!)

        buffer = null
    }
}