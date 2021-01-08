package tech.kzen.auto.server.objects.report.pipeline.calc


// NB: used from expressions, e.g. CalculatedColumnEvalTest
@Suppress("unused")
data class ColumnValue(
    val text: String
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun toText(value: Any?): String {
            return when (value) {
                null -> "null"
                is String -> value
                is ColumnValue -> value.text
                is Number -> ofNumber(value.toDouble()).text
                else -> value.toString()
            }
        }


        fun ofNumber(number: Double): ColumnValue {
//            val asString = number.toString()
//
//            val value =
//                if (asString.endsWith(".0")) {
//                    asString.substring(0, asString.length - 2)
//                }
//                else {
//                    asString
//                }
            val value = ColumnValueUtils.formatDecimal(number)
            return ColumnValue(value)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    operator fun plus(that: Number): ColumnValue {
//        val thisNumber = text.toDoubleOrNull()
        val thisNumber = ColumnValueUtils.toDoubleOrNan(text)
        val thatNumber = that.toDouble()

//        if (thisNumber != null) {
        if (! thisNumber.isNaN()) {
            val addition = thisNumber + thatNumber
            return ofNumber(addition)
        }

        return ColumnValue(text + that.toString())
    }


    operator fun plus(that: ColumnValue): ColumnValue {
//        val thisNumber = text.toDoubleOrNull()
        val thisNumber = ColumnValueUtils.toDoubleOrNan(text)
//        val thatNumber = that.text.toDoubleOrNull()
        val thatNumber = ColumnValueUtils.toDoubleOrNan(that.text)

//        if (thisNumber != null && thatNumber != null) {
        if (! thisNumber.isNaN() && ! thatNumber.isNaN()) {
            val addition = thisNumber + thatNumber
            return ofNumber(addition)
        }

        return ColumnValue(text + that.text)
    }


    operator fun plus(that: Any?): ColumnValue {
//        val thisNumber = text.toDoubleOrNull()
        val thisNumber = ColumnValueUtils.toDoubleOrNan(text)
        val thatText = that.toString()
//        val thatNumber = thatText.toDoubleOrNull()
        val thatNumber = ColumnValueUtils.toDoubleOrNan(thatText)

//        if (thisNumber != null && thatNumber != null) {
        if (! thisNumber.isNaN() && ! thatNumber.isNaN()) {
            val addition = thisNumber + thatNumber
            return ofNumber(addition)
        }

        return ColumnValue(text + thatText)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun toString(): String {
        return text
    }
}