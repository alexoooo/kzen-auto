package tech.kzen.auto.server.objects.report.input.read

import tech.kzen.auto.server.objects.report.input.model.RecordLineBuffer
import tech.kzen.auto.server.objects.report.input.parse.RecordLineParser
import java.io.Reader


class RecordLineReader(
    private val reader: Reader,
    private val recordLineParser: RecordLineParser
):
    AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
    private val contentChars = CharArray(8 * 1024)
    private var contentOffset = 0
    private var contentLength = 0


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * @return true if has next
     */
    fun read(recordLineBuffer: RecordLineBuffer): Boolean {
        if (contentOffset == -1) {
            return false
        }

        if (contentLength == 0) {
            contentLength = reader.read(contentChars)
            if (contentLength == -1) {
                return false
            }
        }

        val recordLength = recordLineParser.parseNext(
            recordLineBuffer, contentChars, contentOffset, contentLength)

        if (recordLength != -1) {
            contentOffset += recordLength
            return true
        }

        while (true) {
            contentOffset = 0
            contentLength = reader.read(contentChars)

            if (contentLength == -1) {
                recordLineParser.endOfStream(recordLineBuffer)
                return false
            }

            val continuedRecordLength = recordLineParser.parseNext(
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