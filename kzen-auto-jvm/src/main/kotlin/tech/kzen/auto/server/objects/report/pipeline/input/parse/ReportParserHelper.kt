package tech.kzen.auto.server.objects.report.pipeline.input.parse

import tech.kzen.auto.server.objects.report.pipeline.input.ReportDataFeeder
import tech.kzen.auto.server.objects.report.pipeline.input.model.BinaryDataBuffer
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordItemBuffer
import java.nio.file.Path



object ReportParserHelper {
    //-----------------------------------------------------------------------------------------------------------------
    fun readHeaderLine(inputPath: Path): List<String> {
        val dataBuffer = BinaryDataBuffer.ofEmpty()
        val headerBuffer = RecordItemBuffer()

        return ReportDataFeeder.single(inputPath).use { feeder ->
            feeder.poll(dataBuffer)
            val parser = RecordParser.forExtension(dataBuffer.innerExtension!!)
            var length = parser.parseNext(headerBuffer, dataBuffer.contents, 0, dataBuffer.length)

            while (length == -1 && feeder.poll(dataBuffer)) {
                length = parser.parseNext(
                    headerBuffer, dataBuffer.contents, 0, dataBuffer.length)
            }

            headerBuffer.toList()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun csvRecords(text: String, bufferSize: Int = text.length): List<RecordItemBuffer> {
        return readRecords(
            text,
            RecordParser.forExtension(RecordParser.csvExtension),
            bufferSize)
    }


    fun tsvRecords(text: String, bufferSize: Int = text.length): List<RecordItemBuffer> {
        return readRecords(
            text,
            RecordParser.forExtension(RecordParser.tsvExtension),
            bufferSize)
    }


    private fun readRecords(
        text: String,
        parser: RecordParser,
        bufferSize: Int = text.length
    ): List<RecordItemBuffer> {
        val contentChars = text.toCharArray()
        val recordLines = mutableListOf<RecordItemBuffer>()

        var contentOffset = 0
        val recordBuffer = RecordItemBuffer()
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

            if (length != -1 && ! recordBuffer.isEmpty()) {
                recordLines.add(recordBuffer.prototype())
                recordBuffer.clear()
            }
        }

        parser.endOfStream(recordBuffer)

        if (! recordBuffer.isEmpty()) {
            recordLines.add(recordBuffer)
        }

        return recordLines
    }
}