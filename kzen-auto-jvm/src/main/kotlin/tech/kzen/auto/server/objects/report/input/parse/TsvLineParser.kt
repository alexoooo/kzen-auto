package tech.kzen.auto.server.objects.report.input.parse

import tech.kzen.auto.server.objects.report.input.model.RecordLineBuffer


class TsvLineParser: RecordLineParser {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        const val stateStartOfField = 0
        const val stateInField = 1
        const val stateEndOfRecord = 2

        const val delimiter = '\t'
        const val carriageReturn = '\r'
        const val lineFeed = '\n'


        fun parseLine(line: String): RecordLineBuffer {
            val parse = TsvLineParser()
            val buffer = RecordLineBuffer()
            parse.parseNext(buffer, line.toCharArray(), 0)
            parse.endOfStream(buffer)
            return buffer
        }


        fun parseLines(lines: String): List<RecordLineBuffer> {
            val lineBuffers = mutableListOf<RecordLineBuffer>()
            val chars = lines.toCharArray()
            val parser = TsvLineParser()

            var buffer = RecordLineBuffer()

            var startIndex = 0
            while (true) {
                val length = parser.parseNext(buffer, chars, startIndex)
                if (length == -1) {
                    break
                }

                lineBuffers.add(buffer)
                buffer = RecordLineBuffer()
                startIndex += length
            }

            parser.endOfStream(buffer)
            if (! buffer.isEmpty()) {
                lineBuffers.add(buffer)
            }

            return lineBuffers
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var state = stateStartOfField


    //-----------------------------------------------------------------------------------------------------------------
    override fun endOfStream(
        recordLineBuffer: RecordLineBuffer
    ): Boolean {
        return parse(recordLineBuffer, lineFeed)
    }


    /**
     * @return true if reached end of record
     */
    override fun parse(
        recordLineBuffer: RecordLineBuffer,
        nextChar: Char
    ): Boolean {
        val nextState = when (state) {
            stateStartOfField -> when (nextChar) {
                delimiter -> {
                    recordLineBuffer.commitField()
                    stateStartOfField
                }

                carriageReturn, lineFeed -> {
                    recordLineBuffer.commitField()
                    stateEndOfRecord
                }

                else -> {
                    recordLineBuffer.addToField(nextChar)
                    stateInField
                }
            }

            stateEndOfRecord -> when (nextChar) {
                delimiter -> {
                    recordLineBuffer.commitField()
                    stateStartOfField
                }

                carriageReturn, lineFeed ->
                    stateEndOfRecord

                else -> {
                    recordLineBuffer.addToField(nextChar)
                    stateInField
                }
            }

            stateInField -> when (nextChar) {
                delimiter -> {
                    recordLineBuffer.commitField()
                    stateStartOfField
                }

                carriageReturn, lineFeed -> {
                    recordLineBuffer.commitField()
                    stateEndOfRecord
                }

                else -> {
                    recordLineBuffer.addToField(nextChar)
                    stateInField
                }
            }

            else ->
                error("Unknown state: $state")
        }

        val previousState = state
        state = nextState

        return state == stateEndOfRecord &&
                previousState != stateEndOfRecord
    }
}