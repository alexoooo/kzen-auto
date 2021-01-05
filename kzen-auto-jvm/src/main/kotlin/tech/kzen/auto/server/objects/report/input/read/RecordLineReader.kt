package tech.kzen.auto.server.objects.report.input.read

import tech.kzen.auto.server.objects.report.input.model.RecordLineBuffer
import tech.kzen.auto.server.objects.report.input.parse.RecordLineParser
import java.io.Reader
import java.io.StringReader


// TODO: decouple reading from parsing (for performance)
class RecordLineReader(
    private val reader: Reader,
    private val recordLineParser: RecordLineParser,
    bufferSize: Int = 8 * 1024
):
    AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun readLines(
            lines: String,
            parser: RecordLineParser,
            bufferSize: Int = lines.length
        ): List<RecordLineBuffer> {
            val reader = RecordLineReader(
                StringReader(lines),
                parser,
                bufferSize)

            val recordLines = mutableListOf<RecordLineBuffer>()

            while (true) {
                val record = RecordLineBuffer()
                val hasNext = reader.read(record)
                if (! record.isEmpty()) {
                    recordLines.add(record)
                }
                if (! hasNext) {
                    break
                }
            }

            return recordLines
        }


        fun csvLines(lines: String, bufferSize: Int = lines.length): List<RecordLineBuffer> {
            return readLines(
                lines,
                RecordLineParser.forExtension(RecordLineParser.csvExtension),
                bufferSize)
        }


        fun tsvLines(lines: String, bufferSize: Int = lines.length): List<RecordLineBuffer> {
            return readLines(
                lines,
                RecordLineParser.forExtension(RecordLineParser.tsvExtension),
                bufferSize)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val contentChars = CharArray(bufferSize.coerceAtLeast(1))
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