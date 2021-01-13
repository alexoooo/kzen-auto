package tech.kzen.auto.server.objects.report.pipeline.input.parse

import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordItemBuffer
import java.io.Reader


class RecordReader(
    private val reader: Reader,
    private val recordParser: RecordParserOld,
    bufferSize: Int = 64 * 1024
):
    AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
    private val contentChars = CharArray(bufferSize.coerceAtLeast(1))
    private var contentOffset = 0
    private var contentLength = 0


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * @return true if has next
     */
    fun read(recordLineBuffer: RecordItemBuffer): Boolean {
        if (contentOffset == -1) {
            return false
        }

        if (contentLength == 0) {
            contentLength = reader.read(contentChars)
            if (contentLength == -1) {
                return false
            }
        }

        val recordLength = recordParser.parseNext(
            recordLineBuffer, contentChars, contentOffset, contentLength)

        if (recordLength != -1) {
            contentOffset += recordLength
            return true
        }

        while (true) {
            contentOffset = 0
            contentLength = reader.read(contentChars)

            if (contentLength == -1) {
                recordParser.endOfStream(recordLineBuffer)
                return false
            }

            val continuedRecordLength = recordParser.parseNext(
                recordLineBuffer, contentChars, contentOffset, contentLength)

            if (continuedRecordLength != -1) {
                contentOffset += continuedRecordLength
                return true
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
        reader.close()
    }
}