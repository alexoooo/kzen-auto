package tech.kzen.auto.server.objects.report.input.model

import java.util.*


class RecordTextFlyweight(
    val recordLineBuffer: RecordLineBuffer,
):
    CharSequence
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = standalone("")

        private val decimalLongPowers = longArrayOf(
            1,
            10,
            100,
            1_000,
            10_000,
            100_000,
            1_000_000,
            10_000_000,
            100_000_000,
            1_000_000_000,
            10_000_000_000,
            100_000_000_000,
            1_000_000_000_000,
            10_000_000_000_000,
            100_000_000_000_000,
            1_000_000_000_000_000,
            10_000_000_000_000_000,
            100_000_000_000_000_000,
            1_000_000_000_000_000_000
        )

        fun standalone(value: String): RecordTextFlyweight {
            val chars = value.toCharArray()
            val buffer = RecordLineBuffer.of(chars, 0, chars.size)
            buffer.selectFlyweight(0)
            return buffer.flyweight
        }

//        fun standaloneSlow(value: String): RecordTextFlyweight {
//            val chars = value.toCharArray()
//            val buffer = RecordLineBuffer()
//            for (c in chars) {
//                buffer.addToField(c)
//            }
//            buffer.commitField()
//            buffer.selectFlyweight(0)
//            return buffer.flyweight
//        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var fieldIndex = -1
    private var valueOffset = 0
    private var valueLength = 0

    private var hashCodeCache: Int = -1


    //-----------------------------------------------------------------------------------------------------------------
    fun select(fieldIndex: Int, valueOffset: Int, valueLength: Int) {
        this.fieldIndex = fieldIndex
        this.valueOffset = valueOffset
        this.valueLength = valueLength
        hashCodeCache = -1
    }


    //-----------------------------------------------------------------------------------------------------------------
    override val length: Int
        get() = valueLength


    override fun get(index: Int): Char {
        return recordLineBuffer.fieldContents[valueOffset + index]
    }


    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        TODO("Not yet implemented")
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun detach(): RecordTextFlyweight {
        val buffer = RecordLineBuffer.of(recordLineBuffer.fieldContents, valueOffset, valueLength)
        val detached = buffer.flyweight
        detached.fieldIndex = 0
        detached.valueOffset = 0
        detached.valueLength = valueLength
        detached.hashCodeCache = hashCodeCache
        return detached
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun trim() {
        val contents = recordLineBuffer.fieldContents

        val initialLength = valueLength
        for (i in 0 until initialLength) {
            if (! Character.isWhitespace(contents[valueOffset])) {
                break
            }

            valueOffset++
            valueLength--
        }

        val afterLeftTrimLength = valueLength
        for (i in (afterLeftTrimLength - 1) downTo 0) {
            if (! Character.isWhitespace(contents[valueOffset + i])) {
                break
            }

            valueLength--
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
//    @Suppress("ConvertTwoComparisonsToRangeCheck")
    fun isDecimal(): Boolean {
        val contents = recordLineBuffer.fieldContents
        val len = valueLength
        val offset = valueOffset

        var dotCount = 0
        var digitCount = 0

        var i = 0
        while (i < len) {
            when (contents[offset + i++]) {
                '.' ->
                    dotCount++

                in '0'..'9' ->
                    digitCount++

                else ->
                    return false
            }
        }

        return digitCount > 0 && dotCount <= 1
    }


    // Shadowed from Java Util Lang, but specialized
    fun toLong(
        offset: Int = 0,
        length: Int = valueLength
    ): Long {
        if (offset + length > valueLength) {
            throw NumberFormatException("$offset - $length - $valueOffset - $valueLength - " + toString())
        }
        if (length <= 0) {
            throw NumberFormatException(toString())
        }

        val contents = recordLineBuffer.fieldContents
        val relativeOffset = valueOffset + offset

        var negative = false
        var i = 0
        var limit = -Long.MAX_VALUE
        var digit: Int

        val firstChar = contents[relativeOffset + i]
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
            digit = contents[relativeOffset + i++] - '0'
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


    fun toDouble(): Double {
        val contents = recordLineBuffer.fieldContents
        val len = valueLength
        val offset = valueOffset

        var pointIndex = -1
        for (i in 0 until len) {
            if (contents[offset + i] == '.') {
                pointIndex = i
                break
            }
        }

        if (pointIndex == -1) {
            return toLong().toDouble()
        }
        else if (pointIndex == len - 1) {
            return toLong(0, len - 1).toDouble()
        }

        val wholePart =
            if (pointIndex == 0) {
                0
            }
            else {
                toLong(0, pointIndex)
            }

        // https://math.stackexchange.com/questions/64042/what-are-the-numbers-before-and-after-the-decimal-point-referred-to-in-mathemati/438718#438718
        val fractionDigits = len - pointIndex - 1
        val fractionAsLong: Long = toLong(pointIndex + 1, fractionDigits)
        val factionalPart = fractionAsLong.toDouble() / decimalLongPowers[fractionDigits]
        return wholePart + factionalPart
    }


    fun toDoubleOrNan(): Double {
        val len = valueLength
        if (len == 0) {
            return Double.NaN
        }

        val contents = recordLineBuffer.fieldContents
        val offset = valueOffset

        var pointIndex = -1
        for (i in 0 until len) {
            val nextChar = contents[offset + i]
            if (nextChar == '.') {
                if (pointIndex != -1 ||
                        len == 1 ||
                        len == 2 && (
                            contents[offset] == '+' ||
                            contents[offset] == '-')
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
            return toLong().toDouble()
        }
        else if (pointIndex == len - 1) {
            return toLong(0, len - 1).toDouble()
        }

        val wholePart =
            if (pointIndex == 0) {
                0
            }
            else {
                toLong(0, pointIndex)
            }

        // https://math.stackexchange.com/questions/64042/what-are-the-numbers-before-and-after-the-decimal-point-referred-to-in-mathemati/438718#438718
        val fractionDigits = len - pointIndex - 1

        var fractionLeadingZeroes = 0
        for (i in 1 .. fractionDigits) {
            if (contents[pointIndex + i] != '0') {
                break
            }
            fractionLeadingZeroes++
        }

        val factionDigitsWithoutLeadingZeroes = fractionDigits - fractionLeadingZeroes
        val fractionAsLongWithoutLeadingZeroes: Long =
            toLong(pointIndex + fractionLeadingZeroes + 1, factionDigitsWithoutLeadingZeroes)
        val factionalPartWithoutLeadingZeroes =
            fractionAsLongWithoutLeadingZeroes.toDouble() / decimalLongPowers[factionDigitsWithoutLeadingZeroes]

        val factionalPart =
            if (fractionLeadingZeroes == 0) {
                factionalPartWithoutLeadingZeroes
            }
            else {
                factionalPartWithoutLeadingZeroes / decimalLongPowers[fractionLeadingZeroes]
            }

        return wholePart + factionalPart
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun toString(): String {
        return String(recordLineBuffer.fieldContents, valueOffset, length)
    }


    override fun hashCode(): Int {
        if (hashCodeCache != -1) {
            return hashCodeCache
        }

        val contents = recordLineBuffer.fieldContents
        val offset = valueOffset
        val end = offset + valueLength

        var result = 1
        for (i in offset until end) {
            result = 31 * result + contents[i].toInt()
        }

        hashCodeCache = result
        return result
    }


    override fun equals(other: Any?): Boolean {
        val that = other as? RecordTextFlyweight
            ?: return false

        val len = valueLength
        if (len != that.valueLength) {
            return false
        }

        if (hashCode() != that.hashCode()) {
            return false
        }

        val contents = recordLineBuffer.fieldContents
        val offset = valueOffset
        val thatContents = that.recordLineBuffer.fieldContents
        val thatValueOffset = that.valueOffset

        @Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
        return Arrays.equals(
            contents, offset, offset + len,
            thatContents, thatValueOffset, thatValueOffset + len)
    }
}