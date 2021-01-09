package tech.kzen.auto.server.objects.report.pipeline.output.pivot.row.signature.store

import com.google.common.primitives.Longs
import tech.kzen.auto.server.objects.report.pipeline.output.pivot.row.signature.RowSignature
import tech.kzen.auto.server.objects.report.pipeline.output.pivot.store.StoreUtils
import java.io.ByteArrayOutputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path


class FileIndexedSignatureStore(
    private val file: Path,
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

    private val writeBufferBytes = ByteArrayOutputStream()


    //-----------------------------------------------------------------------------------------------------------------
    override fun signatureSize(): Int {
        return signatureSize
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun get(signatureOrdinal: Long): RowSignature {
        val offset = signatureOrdinal * buffer.size
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


    override fun add(valueIndexes: LongArray) {
        add(RowSignature(valueIndexes.clone()))
    }


    fun addAll(signatures: List<RowSignature>) {
        seek(fileSize)

        for (signature in signatures) {
            for ((i, valueIndex) in signature.valueIndexes.withIndex()) {
                var remainingBytes = valueIndex
                for (j in 7 downTo 0) {
                    buffer[i * Long.SIZE_BYTES + j] = (remainingBytes and 0xffL).toByte()
                    remainingBytes = remainingBytes shr 8
                }
            }

            writeBufferBytes.write(buffer)
        }

        val bytes = writeBufferBytes.toByteArray()
        writeBufferBytes.reset()
        handle.write(bytes)

        fileSize += bytes.size
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
        StoreUtils.flushAndClose(handle, file.toString())
    }
}