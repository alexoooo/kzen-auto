package tech.kzen.auto.server.objects.report.exec.calc

import tech.kzen.auto.plugin.util.NumberParseUtils
import kotlin.math.abs


// NB: used from expressions, e.g. CalculatedColumnEvalTest
// NB: would be nice to have some kind of "truth" conversion (i.e. toBoolean),
//  e.g. https://discuss.kotlinlang.org/t/can-a-compiler-plugin-add-new-syntax-to-kotlin/23651
@Suppress("unused", "MemberVisibilityCanBePrivate")
class ColumnValue(
    private var asText: String?,
    private var asNumber: Double?
):
    CharSequence, Comparable<Any>
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        const val errorText = "<error>"
        val errorValue = ColumnValue(errorText, Double.NaN)

        private const val epsilon = 0.000_000_000_1

        private const val maxTimeTextLength = 1024

        private val emptyValue = ColumnValue("", Double.NaN)
        private val nullValue = ColumnValue("null", Double.NaN)
        private val zeroValue = ColumnValue("0", 0.0)
        private val oneValue = ColumnValue("1", 1.0)
        private val nanValue = ColumnValue("NaN", Double.NaN)
        private val trueValue = ColumnValue("true", Double.NaN)
        private val trueUpperValue = ColumnValue("TRUE", Double.NaN)
        private val falseValue = ColumnValue("false", Double.NaN)
        private val falseUpperValue = ColumnValue("FALSE", Double.NaN)
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


        fun ofScalar(value: Any?): ColumnValue {
            return when (value) {
                null -> nullValue
                is ColumnValue -> value
                is Number -> ofNumber(value.toDouble())
                else -> ofText(value.toString())
            }
        }


        fun ofText(text: String): ColumnValue {
            when (text) {
                "" -> return emptyValue
                "0" -> return zeroValue
                "1" -> return oneValue
                "NaN" -> return nanValue
                "true" -> return trueValue
                "TRUE" -> return trueUpperValue
                "y" -> return yLowerValue
                "Y" -> return yUpperValue
                "yes" -> return yesValue
                "false" -> return falseValue
                "FALSE" -> return falseUpperValue
                "n" -> return nLowerValue
                "N" -> return nUpperValue
                "no" -> return noValue
                "null" -> return nullValue
                errorText -> return errorValue
            }

            return ColumnValue(text, null)
        }


        fun ofTextNan(text: String): ColumnValue {
            when (text) {
                "" -> return emptyValue
                "NaN" -> return nanValue
                "true" -> return trueValue
                "TRUE" -> return trueUpperValue
                "y" -> return yLowerValue
                "Y" -> return yUpperValue
                "yes" -> return yesValue
                "false" -> return falseValue
                "FALSE" -> return falseUpperValue
                "n" -> return nLowerValue
                "N" -> return nUpperValue
                "no" -> return noValue
                "null" -> return nullValue
                errorText -> return errorValue
            }

            return ColumnValue(text, Double.NaN)
        }


        fun ofNumber(number: Double): ColumnValue {
            if (number.isNaN()) {
                return nanValue
            }

            // NB: + 0 for negative zero normalization
            val normalized = number + 0
            when (normalized) {
                0.0 -> return zeroValue
                1.0 -> return oneValue
            }

            return ColumnValue(null, normalized)
        }


        private fun compareNumbersWithEpsilon(a: Double, b: Double): Int {
            return when {
                abs(a - b) < epsilon ->
                    0

                else ->
                    a.compareTo(b)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    val text: String
        get() = toString()


    val number: Double
        get() = toDoubleOrNan()


    val isNumber: Boolean
        get() = ! number.isNaN()


    val isNaN: Boolean
        get() = text == "NaN"


    val isNull: Boolean
        get() = text == "null"


    val isBlank: Boolean
        get() = isBlank()


    val isTrue: Boolean
        get() = text.equals("true", true)


    // TODO: rename or alias as `logic`? add special If construct?
    val truthy: Boolean get() {
        if (! number.isNaN()) {
            return asNumber == 1.0
        }

        val asString = asText!!
        return when (asString.length) {
            1 -> asString == "y" || asString == "Y"
            3 -> asString.equals("yes", true)
            4 -> asString.equals("true", true)
            else -> false
        }
    }


    @Suppress("SpellCheckingInspection")
    val falsy: Boolean get() {
        if (! number.isNaN()) {
            return asNumber == 0.0
        }

        val asString = asText!!
        return when (asString.length) {
            0 -> true
            1 -> asString == "n" || asString == "N"
            2 -> asString.equals("no", true)
            3 -> asString == "NaN"
            4 -> asString == "null"
            5 -> asString.equals("false", true)
            else -> false
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
            truthy -> falseValue
            falsy -> trueValue
            else -> errorValue
        }
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
    override fun compareTo(other: Any): Int {
        if (eq(other)) {
            return 0
        }

        return when (other) {
            is ColumnValue -> {
                val thisNumber = number
                val otherNumber = other.number

                if (! thisNumber.isNaN()) {
                    if (! otherNumber.isNaN()) {
                        compareNumbersWithEpsilon(thisNumber, otherNumber)
                    }
                    else {
                        -1
                    }
                }
                else {
                    if (! otherNumber.isNaN()) {
                        1
                    }
                    else {
                        text.compareTo(other.text)
                    }
                }
            }

            is Number -> {
                val thisNumber = number
                val otherNumber = other.toDouble()

                if (! thisNumber.isNaN()) {
                    if (! otherNumber.isNaN()) {
                        compareNumbersWithEpsilon(thisNumber, otherNumber)
                    }
                    else {
                        -1
                    }
                }
                else {
                    1
                }
            }

            else ->
                text.compareTo(other.toString())
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
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


    //-----------------------------------------------------------------------------------------------------------------
    infix fun eq(other: Any?): Boolean {
        return when (other) {
            is Number -> {
                val thisNumber = number
                if (thisNumber.isNaN()) {
                    other.toDouble().isNaN() && text == "NaN"
                }
                else {
                    val otherNumber = other.toDouble()
                    if (otherNumber.isNaN()) {
                        false
                    }
                    else {
                        compareNumbersWithEpsilon(thisNumber, otherNumber) == 0
                    }
                }
            }

            else ->
                toString() == other.toString()
        }
    }


    infix fun ne(other: Any?): Boolean {
        return ! eq(other)
    }


    // https://discuss.kotlinlang.org/t/overloading-with-different-types-of-operands/4059/23
    override fun equals(other: Any?): Boolean {
        return this eq other
    }


    override fun hashCode(): Int {
        return toString().hashCode()
    }
}