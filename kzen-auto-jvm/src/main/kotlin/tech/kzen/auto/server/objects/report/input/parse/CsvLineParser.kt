package tech.kzen.auto.server.objects.report.input.parse

//import tech.kzen.auto.server.objects.report.input.model.RecordLineBuffer
//
//
//class CsvLineParser: RecordLineParser {
//    //-----------------------------------------------------------------------------------------------------------------
//    companion object {
//        const val stateStartOfField = 0
//        const val stateInQuoted = 1
//        const val stateInQuotedQuote = 2
//        const val stateInUnquoted = 3
//        const val stateEndOfRecord = 4
//
//        const val quotation = '"'
//        const val delimiter = ','
//        const val carriageReturn = '\r'
//        const val lineFeed = '\n'
//
//
//        fun parseLine(line: String): RecordLineBuffer {
//            val parser = CsvLineParser()
//            val buffer = RecordLineBuffer()
//            parser.parseNext(buffer, line.toCharArray(), 0)
//            parser.endOfStream(buffer)
//            return buffer
//        }
//
//
//        fun parseLines(lines: String): List<RecordLineBuffer> {
//            val lineBuffers = mutableListOf<RecordLineBuffer>()
//            val chars = lines.toCharArray()
//            val parser = CsvLineParser()
//
//            var buffer = RecordLineBuffer()
//
//            var startIndex = 0
//            while (true) {
//                val length = parser.parseNext(buffer, chars, startIndex)
//                if (length == -1) {
//                    break
//                }
//
//                lineBuffers.add(buffer)
//                buffer = RecordLineBuffer()
//                startIndex += length
//            }
//
//            parser.endOfStream(buffer)
//            if (! buffer.isEmpty()) {
//                lineBuffers.add(buffer)
//            }
//
//            return lineBuffers
//        }
//
//
//        fun isSpecial(content: Char): Boolean {
//            return content == quotation ||
//                    content == delimiter ||
//                    content == carriageReturn ||
//                    content == lineFeed ||
//                    content < ' ' ||
//                    content > '~'
//        }
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    private var state = stateStartOfField
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override fun endOfStream(
//        recordLineBuffer: RecordLineBuffer
//    ): Boolean {
//        return parse(recordLineBuffer, lineFeed)
//    }
//
//
//    /**
//     * @return true if reached end of record
//     */
//    override fun parse(
//        recordLineBuffer: RecordLineBuffer,
//        nextChar: Char
//    ): Boolean {
//        val nextState = when (state) {
//            stateStartOfField -> when (nextChar) {
//                quotation ->
//                    stateInQuoted
//
//                delimiter -> {
//                    recordLineBuffer.commitField()
//                    stateStartOfField
//                }
//
//                carriageReturn, lineFeed -> {
//                    recordLineBuffer.commitField()
//                    stateEndOfRecord
//                }
//
//                else -> {
//                    recordLineBuffer.addToField(nextChar)
//                    stateInUnquoted
//                }
//            }
//
//            stateEndOfRecord -> when (nextChar) {
//                quotation ->
//                    stateInQuoted
//
//                delimiter -> {
//                    recordLineBuffer.commitField()
//                    stateStartOfField
//                }
//
//                carriageReturn, lineFeed ->
//                    stateEndOfRecord
//
//                else -> {
//                    recordLineBuffer.addToField(nextChar)
//                    stateInUnquoted
//                }
//            }
//
//            stateInUnquoted -> when (nextChar) {
//                delimiter -> {
//                    recordLineBuffer.commitField()
//                    stateStartOfField
//                }
//
//                carriageReturn, lineFeed -> {
//                    recordLineBuffer.commitField()
//                    stateEndOfRecord
//                }
//
//                quotation ->
//                    error("Unexpected: $nextChar - ${recordLineBuffer.toCsv()}")
//
//                else -> {
//                    recordLineBuffer.addToField(nextChar)
//                    stateInUnquoted
//                }
//            }
//
//            stateInQuoted -> when (nextChar) {
//                quotation -> {
//                    stateInQuotedQuote
//                }
//
//                else -> {
//                    recordLineBuffer.addToField(nextChar)
//                    stateInQuoted
//                }
//            }
//
//            stateInQuotedQuote -> when (nextChar) {
//                quotation -> {
//                    recordLineBuffer.addToField(quotation)
//                    stateInQuoted
//                }
//
//                delimiter -> {
//                    recordLineBuffer.commitField()
//                    stateStartOfField
//                }
//
//                carriageReturn, lineFeed -> {
//                    recordLineBuffer.commitField()
//                    stateEndOfRecord
//                }
//
//                else ->
//                    error("unexpected: $nextChar")
//            }
//
//            else ->
//                error("Unknown state: $state")
//        }
//
//        val previousState = state
//        state = nextState
//
//        return state == stateEndOfRecord &&
//                previousState != stateEndOfRecord
//    }
//}