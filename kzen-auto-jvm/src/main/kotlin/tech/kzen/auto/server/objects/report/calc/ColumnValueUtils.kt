package tech.kzen.auto.server.objects.report.calc

import tech.kzen.auto.server.objects.report.input.model.RecordTextFlyweight


object ColumnValueUtils {
    //-----------------------------------------------------------------------------------------------------------------
    // NB: String.toDoubleOrNull evaluates a regular expression which is very slow, this is an optimization
    fun toDoubleOrNan(text: String): Double {
        val len = text.length
        if (len == 0) {
            return Double.NaN
        }

//        val contents = text.toCharArray()
        val offset = 0

        var pointIndex = -1
        for (i in 0 until len) {
            val nextChar = text[offset + i]
            if (nextChar == '.') {
                if (pointIndex != -1 ||
                    len == 1 ||
                    len == 2 && (
                            text[offset] == '+' ||
                                    text[offset] == '-')
                ) {
                    return Double.NaN
                }
                else {
                    pointIndex = i
                }
            }
            else if (nextChar == '+' || nextChar == '-') {
                if (i != 0 || len == 1) {
                    return Double.NaN
                }
            }
            else if (nextChar !in '0'..'9') {
                return Double.NaN
            }
        }

        if (pointIndex == -1) {
            return toLong(text, 0, len).toDouble()
        }
        else if (pointIndex == len - 1) {
            return toLong(text, 0, len - 1).toDouble()
        }

        val wholePart =
            if (pointIndex == 0) {
                0
            }
            else {
                toLong(text, 0, pointIndex)
            }

        val fractionDigits = len - pointIndex - 1
        var fractionLeadingZeroes = 0
        for (i in 1 .. fractionDigits) {
            if (text[offset + pointIndex + i] != '0') {
                break
            }
            fractionLeadingZeroes++
        }

        val factionDigitsWithoutLeadingZeroes = fractionDigits - fractionLeadingZeroes
        val fractionAsLongWithoutLeadingZeroes: Long =
            if (factionDigitsWithoutLeadingZeroes == 0) {
                0
            }
            else {
                toLong(text, pointIndex + fractionLeadingZeroes + 1, factionDigitsWithoutLeadingZeroes)
            }

        val factionalPartWithoutLeadingZeroes = fractionAsLongWithoutLeadingZeroes.toDouble() /
                RecordTextFlyweight.decimalLongPowers[factionDigitsWithoutLeadingZeroes]

        val factionalPart =
            if (fractionLeadingZeroes == 0) {
                factionalPartWithoutLeadingZeroes
            }
            else {
                factionalPartWithoutLeadingZeroes / RecordTextFlyweight.decimalLongPowers[fractionLeadingZeroes]
            }

        return wholePart + factionalPart
    }


    private fun toLong(text: String, offset: Int, length: Int): Long {
        if (offset + length > text.length) {
            throw NumberFormatException("$offset - $length - ${text.length} - $text")
        }
        if (length <= 0) {
            throw NumberFormatException("offset = $offset, length = $length: $text")
        }

        var negative = false
        var i = 0
        var limit = -Long.MAX_VALUE
        var digit: Int

        val firstChar = text[offset + i]
        if (firstChar < '0') { // Possible leading "+" or "-"
            if (firstChar == '-') {
                negative = true
                limit = Long.MIN_VALUE
            }
            else if (firstChar != '+') {
                throw NumberFormatException(toString())
            }
            i++
            if (length == 1) { // Cannot have lone "+" or "-"
                throw NumberFormatException(toString())
            }
        }

        val multmin = limit / 10
        var result = 0L
        while (i < length) {
            // Accumulating negatively avoids surprises near MAX_VALUE
            digit = text[offset + i++] - '0'
            if (digit < 0 || digit > 9) {
                throw NumberFormatException(toString())
            }
            if (result < multmin) {
                throw NumberFormatException(toString())
            }
            result *= 10
            if (result < limit + digit) {
                throw NumberFormatException(toString())
            }
            result -= digit
        }

        return if (negative) result else -result
    }
}