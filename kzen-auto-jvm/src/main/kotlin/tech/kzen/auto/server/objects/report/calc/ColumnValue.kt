package tech.kzen.auto.server.objects.report.calc


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
            val asString = number.toString()

            val value =
                if (asString.endsWith(".0")) {
                    asString.substring(0, asString.length - 2)
                }
                else {
                    asString
                }

            return ColumnValue(value)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    operator fun plus(that: Number): ColumnValue {
        val thisNumber = text.toDoubleOrNull()
        val thatNumber = that.toDouble()

        if (thisNumber != null) {
            val addition = thisNumber + thatNumber
            return ofNumber(addition)
        }

        return ColumnValue(text + that.toString())
    }


    operator fun plus(that: ColumnValue): ColumnValue {
        val thisNumber = text.toDoubleOrNull()
        val thatNumber = that.text.toDoubleOrNull()

        if (thisNumber != null && thatNumber != null) {
            val addition = thisNumber + thatNumber
            return ofNumber(addition)
        }

        return ColumnValue(text + that.text)
    }
}