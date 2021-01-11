package tech.kzen.auto.server.objects.report.pipeline.calc


// NB: used from expressions, e.g. CalculatedColumnEvalTest
@Suppress("unused")
data class ColumnValue(
    var text: String?,
    var number: Double?
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
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
            return ColumnValue(text, null)
        }


        fun ofTextNan(text: String): ColumnValue {
            return ColumnValue(text, Double.NaN)
        }


        fun ofNumber(number: Double): ColumnValue {
            return ColumnValue(null, number)
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

        return ofTextNan(text + that.toString())
    }


    operator fun plus(that: ColumnValue): ColumnValue {
        val thisNumber = toDoubleOrNan()
        val thatNumber = that.toDoubleOrNan()

        if (! thisNumber.isNaN() && ! thatNumber.isNaN()) {
            val addition = thisNumber + thatNumber
            return ofNumber(addition)
        }

        return ofTextNan(text + that.text)
    }


    operator fun plus(that: Any?): ColumnValue {
        val thisNumber = toDoubleOrNan()
        val thatText = that.toString()
        val thatNumber = ColumnValueUtils.toDoubleOrNan(thatText)

        if (! thisNumber.isNaN() && ! thatNumber.isNaN()) {
            val addition = thisNumber + thatNumber
            return ofNumber(addition)
        }

        return ofTextNan(text + thatText)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun toDoubleOrNan(): Double {
        if (number != null) {
            return number!!
        }
        number = ColumnValueUtils.toDoubleOrNan(text!!)
        return number!!
    }


    override fun toString(): String {
        if (text == null) {
            text = ColumnValueUtils.formatDecimal(number!!)
        }
        return text!!
    }
}