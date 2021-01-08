package tech.kzen.auto.server.objects.report.pipeline.output.pivot.row.digest

import com.google.common.io.BaseEncoding
import com.google.common.primitives.UnsignedBytes
import tech.kzen.auto.server.objects.report.pipeline.output.pivot.store.StoreUtils
import tech.kzen.lib.common.util.Digest
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path


// see: http://sux.di.unimi.it/docs/it/unimi/dsi/sux4j/io/BucketedHashStore.html
class FileDigestIndex(
    private val dir: Path
):
    DigestIndex
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val fileCacheSize = 64
        private const val digestBytes = 4 * Integer.BYTES
        private const val entryBytes = digestBytes + Long.SIZE_BYTES
        private val fileSize = entryBytes * (UnsignedBytes.toInt(UnsignedBytes.MAX_VALUE) + 1)
//        private val fileSize = entryBytes * UnsignedBytes.toInt(UnsignedBytes.MAX_VALUE)
        private val emptyFileBuffer = ByteArray(fileSize)
        private const val absentSentinel = 0
        private const val collisionSentinel = -1

        private val collisionEntry = ByteArray(entryBytes)
        init {
            ByteBuffer
                .wrap(collisionEntry)
                .putInt(collisionSentinel)
                .putInt(collisionSentinel)
                .putInt(collisionSentinel)
                .putInt(collisionSentinel)
                .putLong(-1)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var nextOrdinal = 0L

    private val digestBufferBytes = ByteArray(digestBytes)
    private val digestBuffer = ByteBuffer.wrap(digestBufferBytes)

    private val existingDigestBufferBytes = ByteArray(digestBytes)
    private val existingDigestBuffer = ByteBuffer.wrap(existingDigestBufferBytes)

    private val entryBufferBytes = ByteArray(entryBytes)
    private val entryBuffer = ByteBuffer.wrap(entryBufferBytes)

    private val fileHandleCache = object : LinkedHashMap<String, RandomAccessFile>(
        (fileCacheSize / 0.75 + 1).toInt(), 0.75F, true
    )
    {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, RandomAccessFile>): Boolean {
            if (size < fileCacheSize) {
                return false
            }
            StoreUtils.flushAndClose(eldest.value)
            return true
        }
    }

    private var bucketCount: Long = 0


    //-----------------------------------------------------------------------------------------------------------------
    init {
        Files.createDirectories(dir)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun bucketCount(): Long {
        return bucketCount
    }

    override fun size(): Long {
        return nextOrdinal
    }


    override fun getOrAdd(digestHigh: Long, digestLow: Long): DigestOrdinal {
        val digestA = (digestHigh shr 32).toInt()
        val digestB = digestHigh.toInt()
        val digestC = (digestLow shr 32).toInt()
        val digestD = digestLow.toInt()
        val digest = Digest(digestA, digestB, digestC, digestD)

        check(digest != Digest.zero)

        writeDigest(digest, digestBuffer)

        for (i in digestBufferBytes.indices) {
            val handle = readEntry(i)

            entryBuffer.clear()
            val a = entryBuffer.int
            val b = entryBuffer.int
            val c = entryBuffer.int
            val d = entryBuffer.int

            if (a == collisionSentinel && b == collisionSentinel && c == collisionSentinel && d == collisionSentinel) {
                continue
            }

            return when {
                a == digest.a && b == digest.b && c == digest.c && d == digest.d -> {
                    val ordinal = entryBuffer.long
                    DigestOrdinal.ofExisting(ordinal)
                }

                a == absentSentinel && b == absentSentinel && c == absentSentinel && d == absentSentinel -> {
                    val ordinal = insertEntry(i, handle, digest)
                    DigestOrdinal.ofAdded(ordinal)
                }

                else -> {
                    val existingOrdinal = entryBuffer.long
                    val ordinal = addCollision(i, handle, digest, a, b, c, d, existingOrdinal)
                    DigestOrdinal.ofAdded(ordinal)
                }
            }
        }

        throw IllegalStateException("Not found: $digest")
    }


    private fun writeDigest(digest: Digest, byteBuffer: ByteBuffer) {
        writeDigest(digest.a, digest.b, digest.c, digest.d, byteBuffer)
    }


    private fun writeDigest(a: Int, b: Int, c: Int, d: Int, byteBuffer: ByteBuffer) {
        byteBuffer.putInt(a)
        byteBuffer.putInt(b)
        byteBuffer.putInt(c)
        byteBuffer.putInt(d)
        byteBuffer.clear()
    }


    private fun readEntry(byteIndex: Int): RandomAccessFile {
        val handle = fileUpTo(byteIndex)

        val index = UnsignedBytes.toInt(digestBufferBytes[byteIndex])
        val offset = index * entryBytes
        handle.seek(offset.toLong())

        val read = handle.read(entryBufferBytes)
        check(read == entryBytes) {
            "Error in $dir - ${filenameUpTo(byteIndex)} | $index"
        }

        return handle
    }


    private fun insertEntry(byteIndex: Int, handle: RandomAccessFile, digest: Digest): Long {
        val index = UnsignedBytes.toInt(digestBufferBytes[byteIndex])

        val ordinal = nextOrdinal

        writeEntry(handle, index, digest, ordinal)

        nextOrdinal++
        return ordinal
    }


    private fun writeEntry(handle: RandomAccessFile, index: Int, digest: Digest, ordinal: Long) {
        writeEntry(handle, index, digest.a, digest.b, digest.c, digest.d, ordinal)
    }


    private fun writeEntry(
        handle: RandomAccessFile,
        index: Int,
        digestA: Int,
        digestB: Int,
        digestC: Int,
        digestD: Int,
        ordinal: Long
    ) {
        val offset = index * entryBytes
        handle.seek(offset.toLong())

        entryBuffer.clear()
        entryBuffer.putInt(digestA)
        entryBuffer.putInt(digestB)
        entryBuffer.putInt(digestC)
        entryBuffer.putInt(digestD)
        entryBuffer.putLong(ordinal)

        handle.write(entryBufferBytes)
    }


    private fun addCollision(
        byteIndex: Int,
        handle: RandomAccessFile,
        digest: Digest,
        existingA: Int,
        existingB: Int,
        existingC: Int,
        existingD: Int,
        existingOrdinal: Long
    ): Long {
        val index = UnsignedBytes.toInt(digestBufferBytes[byteIndex])
        handle.seek((index * entryBytes).toLong())
        handle.write(collisionEntry)

        writeDigest(existingA, existingB, existingC, existingD, existingDigestBuffer)

        val ordinal = nextOrdinal
        nextOrdinal++

        for (i in (byteIndex + 1) until digestBufferBytes.size) {
            val nextHandle = fileUpTo(i)

            if (digestBufferBytes[i] == existingDigestBufferBytes[i]) {
                val collisionIndex = UnsignedBytes.toInt(digestBufferBytes[i])
                nextHandle.seek((collisionIndex * entryBytes).toLong())
                nextHandle.write(collisionEntry)
            }
            else {
                val insertIndex = UnsignedBytes.toInt(digestBufferBytes[i])
                writeEntry(nextHandle, insertIndex, digest, ordinal)

                val moveIndex = UnsignedBytes.toInt(existingDigestBufferBytes[i])
                writeEntry(nextHandle, moveIndex, existingA, existingB, existingC, existingD, existingOrdinal)

                return ordinal
            }
        }

        throw IllegalStateException()
    }


    private fun fileUpTo(byteIndex: Int): RandomAccessFile {
        val filename = filenameUpTo(byteIndex)

        val existing = fileHandleCache[filename]
        if (existing != null) {
            return existing
        }

        val path = dir.resolve(filename)

        val alreadyExists = Files.exists(path)

        val handle = RandomAccessFile(path.toFile(), "rw")

        if (! alreadyExists) {
            handle.write(emptyFileBuffer)
            bucketCount++
        }

        fileHandleCache[filename] = handle

        return handle
    }


    private fun filenameUpTo(byteIndex: Int): String {
        if (byteIndex == 0) {
            return "root"
        }

        return BaseEncoding.base16().encode(digestBufferBytes, 0, byteIndex)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
        val iterator = fileHandleCache.iterator()
        while (iterator.hasNext()) {
            iterator.next().value.close()
            iterator.remove()
        }
    }
}