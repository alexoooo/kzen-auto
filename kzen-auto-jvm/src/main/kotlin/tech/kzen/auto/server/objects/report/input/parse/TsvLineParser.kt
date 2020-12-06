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
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var state = stateStartOfField


    //-----------------------------------------------------------------------------------------------------------------
    override fun parseNext(
        recordLineBuffer: RecordLineBuffer,
        contentChars: CharArray,
        contentOffset: Int,
        contentEnd: Int
    ): Int {
        var recordLength = 0
        var i = contentOffset
        while (i < contentEnd) {
            var nextChar = contentChars[i]
            recordLength++

            if (state == stateInField) {
                val length: Int = parseInFieldUntilNextState(
                    recordLineBuffer, contentChars, i, contentEnd, nextChar)
                if (length == -1) {
                    return -1
                }
                i += length
                nextChar = contentChars[i]
                recordLength += length
            }

            val isEnd = parse(recordLineBuffer, nextChar)
            if (isEnd) {
                return recordLength
            }

            i++
        }
        return -1
    }


    private fun parseInFieldUntilNextState(
        recordLineBuffer: RecordLineBuffer,
        contentChars: CharArray,
        start: Int,
        contentEnd: Int,
        startChar: Char
    ): Int {
        var length = 0
        var reachedNextState = false
        var i: Int = start
        var nextChar: Char = startChar
        while (true) {
            if (nextChar == delimiter ||
                    nextChar == carriageReturn ||
                    nextChar == lineFeed
            ) {
                reachedNextState = true
                break
            }
            i++
            if (i == contentEnd) {
                break
            }
            nextChar = contentChars[i]
            length++
        }
        if (! reachedNextState) {
            length++
        }
        recordLineBuffer.addToField(contentChars, start, i - start)
        return if (reachedNextState) length else -1
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun endOfStream(
        recordLineBuffer: RecordLineBuffer
    ): Boolean {
        return parse(recordLineBuffer, lineFeed)
    }


    /**
     * @return true if reached end of record
     */
    private fun parse(
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