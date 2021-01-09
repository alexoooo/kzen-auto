package tech.kzen.auto.server.objects.report.pipeline.input.model

import java.nio.file.Path


data class BinaryDataBuffer(
    var location: Path?,
    var innerExtension: String?,
    var contents: CharArray,
    var length: Int,
    var endOfStream: Boolean
) {
    companion object {
//        private const val defaultBufferSize = 1
//        private const val defaultBufferSize = 2
//        private const val defaultBufferSize = 3
//        private const val defaultBufferSize = 4
//        private const val defaultBufferSize = 5
//        private const val defaultBufferSize = 6
//        private const val defaultBufferSize = 7
//        private const val defaultBufferSize = 8
//        private const val defaultBufferSize = 9
//        private const val defaultBufferSize = 10
//        private const val defaultBufferSize = 11
//        private const val defaultBufferSize = 8 * 1024
        private const val defaultBufferSize = 64 * 1024

        fun ofEmpty(bufferSize: Int = defaultBufferSize): BinaryDataBuffer {
            return BinaryDataBuffer(
                null,
                null,
                CharArray(bufferSize),
                0,
            false)
        }
    }


    fun clear() {
        location = null
        innerExtension = null
        length = 0
        endOfStream = false
    }


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BinaryDataBuffer

        if (location != other.location) return false
        if (!contents.contentEquals(other.contents)) return false
        if (length != other.length) return false

        return true
    }

    override fun hashCode(): Int {
        var result = location.hashCode()
        result = 31 * result + contents.contentHashCode()
        result = 31 * result + length
        return result
    }
}