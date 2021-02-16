package tech.kzen.auto.server.objects.report.pipeline.calc

import tech.kzen.auto.server.objects.report.pipeline.input.parse.NumberParseUtils


// NB: used from expressions, e.g. CalculatedColumnEvalTest
@Suppress("unused", "MemberVisibilityCanBePrivate")
class ColumnValue(
    private var asText: String?,
    private var asNumber: Double?
):
    CharSequence
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        const val errorText = "<error>"
        val errorValue = ColumnValue(errorText, Double.NaN)

        private const val maxTimeTextLength = 1024

        private val emptyValue = ColumnValue("", Double.NaN)
        private val zeroValue = ColumnValue("0", 0.0)
        private val oneValue = ColumnValue("1", 1.0)
        private val trueValue = ColumnValue("true", Double.NaN)
        private val falseValue = ColumnValue("false", Double.NaN)
        private val yLowerValue = ColumnValue("y", Double.NaN)
        private val yUpperValue = ColumnValue("y", Double.NaN)
        private val yesValue = ColumnValue("yes", Double.NaN)
        private val nLowerValue = ColumnValue("n", Double.NaN)
        private val nUpperValue = ColumnValue("N", Double.NaN)
        private val noValue = ColumnValue("no", Double.NaN)


        fun toText(value: Any?): String {
            return when (value) {
                null -> "null"
                is String -> value
                is ColumnValue -> value.toString()
                is Number -> ofNumber(value.toDouble()).toString()
                else -> value.toString()
            }
        }


        fun ofText(text: String): ColumnValue {
            when (text) {
                "" -> return emptyValue
                "0" -> return zeroValue
                "1" -> return oneValue
                "y" -> return yLowerValue
                "Y" -> return yUpperValue
                "yes" -> return yesValue
                "n" -> return nLowerValue
                "N" -> return nUpperValue
                "no" -> return noValue
                errorText -> return errorValue
            }

            return ColumnValue(text, null)
        }


        fun ofTextNan(text: String): ColumnValue {
            when (text) {
                "" -> return emptyValue
                "y" -> return yLowerValue
                "Y" -> return yUpperValue
                "yes" -> return yesValue
                "n" -> return nLowerValue
                "N" -> return nUpperValue
                "no" -> return noValue
                errorText -> return errorValue
            }

            return ColumnValue(text, Double.NaN)
        }


        fun ofNumber(number: Double): ColumnValue {
            // NB: + 0 for negative zero normalization
            val normalized = number + 0
            when (normalized) {
                0.0 -> return zeroValue
                1.0 -> return oneValue
            }

            return ColumnValue(null, normalized)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    operator fun plus(that: Number): ColumnValue {
        val thisNumber = toDoubleOrNan()
        val thatNumber = that.toDouble()

        if (! thisNumber.isNaN()) {
            val addition = thisNumber + thatNumber
            return ofNumber(addition)
        }

        return ofTextNan(asText + that.toString())
    }


    operator fun plus(that: ColumnValue): ColumnValue {
        val thisNumber = toDoubleOrNan()
        val thatNumber = that.toDoubleOrNan()

        if (! thisNumber.isNaN() && ! thatNumber.isNaN()) {
            val addition = thisNumber + thatNumber
            return ofNumber(addition)
        }

        return ofTextNan(asText + that.asText)
    }


    operator fun plus(that: Any?): ColumnValue {
        if (that is String) {
            return ofText(asText + that)
        }

        val thisNumber = toDoubleOrNan()
        val thatText = that.toString()
        val thatNumber = NumberParseUtils.toDoubleOrNan(thatText)

        if (! thisNumber.isNaN() && ! thatNumber.isNaN()) {
            val addition = thisNumber + thatNumber
            return ofNumber(addition)
        }

        return ofTextNan(asText + thatText)
    }


    //-----------------------------------------------------------------------------------------------------------------
    operator fun times(that: Number): ColumnValue {
        val thisNumber = toDoubleOrNan()
        if (thisNumber.isNaN()) {
            return errorValue
        }

        val thatNumber = that.toDouble()
        val multiplication = thisNumber * thatNumber
        return ofNumber(multiplication)
    }


    operator fun times(that: ColumnValue): ColumnValue {
        val thisNumber = toDoubleOrNan()
        if (thisNumber.isNaN()) {
            return errorValue
        }

        val thatNumber = that.toDoubleOrNan()
        if (thatNumber.isNaN()) {
            return errorValue
        }

        val multiplication = thisNumber * thatNumber
        return ofNumber(multiplication)
    }


    operator fun times(that: Any?): ColumnValue {
        val thisNumber = toDoubleOrNan()
        if (thisNumber.isNaN()) {
            return errorValue
        }

        val thatText = that.toString()
        val thatNumber = NumberParseUtils.toDoubleOrNan(thatText)
        if (thatNumber.isNaN()) {
            return errorValue
        }

        val multiplication = thisNumber * thatNumber
        return ofNumber(multiplication)
    }


    //-----------------------------------------------------------------------------------------------------------------
    operator fun div(that: Number): ColumnValue {
        val thisNumber = toDoubleOrNan()
        if (thisNumber.isNaN()) {
            return errorValue
        }

        val thatNumber = that.toDouble()
        val division = thisNumber / thatNumber
        return ofNumber(division)
    }


    operator fun div(that: ColumnValue): ColumnValue {
        val thisNumber = toDoubleOrNan()
        if (thisNumber.isNaN()) {
            return errorValue
        }

        val thatNumber = that.toDoubleOrNan()
        if (thatNumber.isNaN()) {
            return errorValue
        }

        val division = thisNumber / thatNumber
        return ofNumber(division)
    }


    operator fun div(that: Any?): ColumnValue {
        val thisNumber = toDoubleOrNan()
        if (thisNumber.isNaN()) {
            return errorValue
        }

        val thatText = that.toString()
        val thatNumber = NumberParseUtils.toDoubleOrNan(thatText)
        if (thatNumber.isNaN()) {
            return errorValue
        }

        val division = thisNumber / thatNumber
        return ofNumber(division)
    }


    //-----------------------------------------------------------------------------------------------------------------
    operator fun minus(that: Number): ColumnValue {
        val thisNumber = toDoubleOrNan()
        if (thisNumber.isNaN()) {
            return errorValue
        }

        val thatNumber = that.toDouble()
        val subtraction = thisNumber - thatNumber
        return ofNumber(subtraction)
    }


    operator fun minus(that: ColumnValue): ColumnValue {
        val thisNumber = toDoubleOrNan()
        if (thisNumber.isNaN()) {
            return errorValue
        }

        val thatNumber = that.toDoubleOrNan()
        if (thatNumber.isNaN()) {
            return errorValue
        }

        val subtraction = thisNumber - thatNumber
        return ofNumber(subtraction)
    }


    operator fun minus(that: Any?): ColumnValue {
        val thisNumber = toDoubleOrNan()
        if (thisNumber.isNaN()) {
            return errorValue
        }

        val thatText = that.toString()
        val thatNumber = NumberParseUtils.toDoubleOrNan(thatText)
        if (thatNumber.isNaN()) {
            return errorValue
        }

        val subtraction = thisNumber - thatNumber
        return ofNumber(subtraction)
    }


    //-----------------------------------------------------------------------------------------------------------------
    operator fun unaryPlus(): ColumnValue {
        return this
    }


    operator fun unaryMinus(): ColumnValue {
        val asDouble = toDoubleOrNan()
        if (asDouble.isNaN()) {
            return this
        }
        return ofNumber(-asDouble)
    }


    operator fun not(): ColumnValue {
        return when {
            isTruthy() -> falseValue
            isFalsy() -> trueValue
            else -> errorValue
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    val yes: Boolean
        get() = isTruthy()


    fun isTruthy(): Boolean {
        if (asNumber != null && ! asNumber!!.isNaN()) {
            return asNumber == 1.0
        }

        val asString = asText!!

        if (asString.length == 4) {
            return asString == "true" || asString.toLowerCase() == "true"
        }

        if (asString.length == 1) {
            return asString == "y" || asString == "Y" || asString == "1"
        }

        if (asString.length == 3) {
            return asString == "yes" || asString.toLowerCase() == "yes"
        }

        return false
    }


    @Suppress("SpellCheckingInspection")
    fun isFalsy(): Boolean {
        if (asNumber != null && ! asNumber!!.isNaN()) {
            return asNumber == 0.0
        }

        val asString = asText!!

        if (asString.isEmpty()) {
            return true
        }

        if (asString.length == 5) {
            return asString == "false" || asString.toLowerCase() == "false"
        }

        if (asString.length == 1) {
            return asString == "n" || asString == "N" || asString == "0"
        }

        if (asString.length == 2) {
            return asString == "no" || asString.toLowerCase() == "no"
        }

        return false
    }


    //-----------------------------------------------------------------------------------------------------------------
    override val length: Int
        get() = toString().length


    override fun get(index: Int): Char {
        return toString()[index]
    }


    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        return toString().subSequence(startIndex, endIndex)
    }


    //-----------------------------------------------------------------------------------------------------------------
    val number: Double
        get() = toDoubleOrNan()


    val text: String
        get() = toString()


    private fun toDoubleOrNan(): Double {
        if (asNumber != null) {
            return asNumber!!
        }
        asNumber = NumberParseUtils.toDoubleOrNan(asText!!)
        return asNumber!!
    }


    override fun toString(): String {
        if (asText == null) {
            asText = ColumnValueUtils.formatDecimal(asNumber!!)
        }
        return asText!!
    }


    infix fun eq(other: Any?): Boolean {
        if (other is ColumnValue) {
            return toString() == other.toString()
        }
        if (other is Number) {
            return toDoubleOrNan() == other.toDouble()
        }
        return toString() == other.toString()
    }


    infix fun ne(other: Any?): Boolean {
        return ! eq(other)
    }


    // https://discuss.kotlinlang.org/t/overloading-with-different-types-of-operands/4059/23
    override fun equals(other: Any?): Boolean {
        if (other is ColumnValue) {
            return toString() == other.toString()
        }
        return toString() == other.toString()
    }


    override fun hashCode(): Int {
        return toString().hashCode()
    }
}