package tech.kzen.auto.server.objects.report.pipeline.input.parse

import tech.kzen.auto.server.objects.report.pipeline.input.ReportInputDecoder
import tech.kzen.auto.server.objects.report.pipeline.input.ReportInputReader
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordDataBuffer
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordItemBuffer
import java.nio.file.Path



object ReportParserHelper {
    //-----------------------------------------------------------------------------------------------------------------
    fun readHeaderLine(inputPath: Path): List<String> {
        val dataBuffer = RecordDataBuffer.ofBufferSize()
        val headerBuffer =
            RecordItemBuffer(0, 0)
        val decoder = ReportInputDecoder()

        return ReportInputReader.file(inputPath).use { reader ->
            reader.poll(dataBuffer)
            decoder.decode(dataBuffer)
            val parser = RecordParserOld.forExtension(dataBuffer.innerExtension!!)
            var length = parser.parseNext(headerBuffer, dataBuffer.chars, 0, dataBuffer.charsLength)

            while (length == -1 && reader.poll(dataBuffer)) {
                decoder.decode(dataBuffer)
                length = parser.parseNext(
                    headerBuffer, dataBuffer.chars, 0, dataBuffer.charsLength)
            }

            headerBuffer.toList()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun csvRecords(text: String, bufferSize: Int = text.length): List<RecordItemBuffer> {
        return readRecords(
            text,
            RecordParserOld.forExtension(RecordParserOld.csvExtension),
            bufferSize)
    }


    private fun readRecords(
        text: String,
        parser: RecordParserOld,
        bufferSize: Int = text.length
    ): List<RecordItemBuffer> {
        val contentChars = text.toCharArray()
        val recordLines = mutableListOf<RecordItemBuffer>()

        var contentOffset = 0
        val recordBuffer =
            RecordItemBuffer(0, 0)
        while (contentOffset < contentChars.size) {
            val end = (contentOffset + bufferSize).coerceAtMost(contentChars.size)
            val length = parser.parseNext(
                recordBuffer, contentChars, contentOffset, end)

            contentOffset =
                if (length != -1) {
                    contentOffset + length
                }
                else {
                    end
                }

            if (length != -1 && ! recordBuffer.isEmpty) {
                recordLines.add(recordBuffer.prototype())
                recordBuffer.clear()
            }
        }

        parser.endOfStream(recordBuffer)

        if (! recordBuffer.isEmpty) {
            recordLines.add(recordBuffer)
        }

        return recordLines
    }
}