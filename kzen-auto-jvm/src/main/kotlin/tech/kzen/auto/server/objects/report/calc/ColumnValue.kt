package tech.kzen.auto.server.objects.report.calc


// NB: used from expressions, e.g. CalculatedColumnEvalTest
@Suppress("unused")
data class ColumnValue(
    val text: String
)//:
//    CharSequence,
//    Number()
{
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


    operator fun plus(that: Any?): ColumnValue {
        val thisNumber = text.toDoubleOrNull()
        val thatText = that.toString()
        val thatNumber = thatText.toDoubleOrNull()

        if (thisNumber != null && thatNumber != null) {
            val addition = thisNumber + thatNumber
            return ofNumber(addition)
        }

        return ColumnValue(text + thatText)
    }


//    //-----------------------------------------------------------------------------------------------------------------
//    override val length: Int
//        get() = text.length
//
//    override fun get(index: Int): Char {
//        return text[index]
//    }
//
//    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
//        return text.subSequence(startIndex, endIndex)
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override fun toByte(): Byte {
//        return text.toByteOrNull() ?: 0
//    }
//
//    override fun toChar(): Char {
//        return if (text.isEmpty()) { ' ' } else { text[0] }
//    }
//
//    override fun toDouble(): Double {
//        return text.toDoubleOrNull() ?: 0.0
//    }
//
//    override fun toFloat(): Float {
//        return text.toFloatOrNull() ?: 0.0f
//    }
//
//    override fun toInt(): Int {
//        return text.toIntOrNull() ?: 0
//    }
//
//    override fun toLong(): Long {
//        return text.toLongOrNull() ?: 0
//    }
//
//    override fun toShort(): Short {
//        return text.toShortOrNull() ?: 0
//    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun toString(): String {
        return text
    }
}