package tech.kzen.auto.server.objects.report.pipeline.input.model

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.file.Path


class RecordDataBuffer(
    var location: Path?,
    var innerExtension: String?,

    val bytes: ByteArray,
    var bytesLength: Int,

    val chars: CharArray,
    var charsLength: Int,

    var endOfStream: Boolean,

    val recordTokenBuffer: RecordTokenBuffer
) {
    //----------------------------------------------------------------------------------------------------------------
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
//        private const val defaultBufferSize = 128 * 1024


        // maxUnicodeSize https://stijndewitt.com/2014/08/09/max-bytes-in-a-utf-8-char/
        const val minBufferSize = 4

        fun ofBufferSize(bufferSize: Int = defaultBufferSize): RecordDataBuffer {
            check(bufferSize >= minBufferSize) {
                "Size must be at least $minBufferSize = $bufferSize"
            }

            return RecordDataBuffer(
                null,
                null,

                ByteArray(bufferSize),
                0,

                CharArray(bufferSize + minBufferSize),
//                CharArray(bufferSize),
                0,

            false,

                RecordTokenBuffer())
        }
    }


    val byteBuffer = ByteBuffer.wrap(bytes)
    val charBuffer = CharBuffer.wrap(chars)


    //----------------------------------------------------------------------------------------------------------------
//    fun clear() {
//        location = null
//        innerExtension = null
//        bytesLength = 0
//        textLength = 0
//        endOfStream = false
//    }
}