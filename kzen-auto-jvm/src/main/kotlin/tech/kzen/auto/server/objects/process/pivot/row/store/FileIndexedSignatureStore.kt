package tech.kzen.auto.server.objects.process.pivot.row.store

import com.google.common.primitives.Longs
import tech.kzen.auto.server.objects.process.pivot.row.signature.RowSignature
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path


class FileIndexedSignatureStore(
    file: Path,
    private var signatureSize: Int
): IndexedSignatureStore {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private fun signatureBytes(size: Int): Int {
            return size * Long.SIZE_BYTES;
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var fileSize: Long =
        if (! Files.exists(file)) {
            Files.createDirectories(file.parent)
            0
        }
        else {
            Files.size(file)
        }

    private val handle: RandomAccessFile =
        RandomAccessFile(file.toFile(), "rw")

    private var buffer = ByteArray(signatureBytes(signatureSize))
    private var previousOffset = 0L


    //-----------------------------------------------------------------------------------------------------------------
    override fun get(signatureIndex: Long): RowSignature {
        val offset = signatureIndex * buffer.size
        seek(offset)
        handle.read(buffer)
        previousOffset = offset + buffer.size

        val valueIndexes = LongArray(signatureSize)
        for (i in valueIndexes.indices) {
            valueIndexes[i] = Longs.fromBytes(
                buffer[i * Long.SIZE_BYTES],
                buffer[i * Long.SIZE_BYTES + 1],
                buffer[i * Long.SIZE_BYTES + 2],
                buffer[i * Long.SIZE_BYTES + 3],
                buffer[i * Long.SIZE_BYTES + 4],
                buffer[i * Long.SIZE_BYTES + 5],
                buffer[i * Long.SIZE_BYTES + 6],
                buffer[i * Long.SIZE_BYTES + 7]
            )
        }

        return RowSignature(valueIndexes)
    }


    override fun add(signature: RowSignature) {
        seek(fileSize)

        for ((i, valueIndex) in signature.valueIndexes.withIndex()) {
            var remainingBytes = valueIndex
            for (j in 7 downTo 0) {
                buffer[i * Long.SIZE_BYTES + j] = (remainingBytes and 0xffL).toByte()
                remainingBytes = remainingBytes shr 8
            }
        }

        handle.write(buffer)
        fileSize += buffer.size
        previousOffset = fileSize
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun seek(offset: Long) {
        if (previousOffset == offset) {
            return
        }

        handle.seek(offset)
        previousOffset = offset
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
        handle.close()
    }
}