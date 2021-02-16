package tech.kzen.auto.server.objects.report.pipeline.calc


@Suppress("unused", "MemberVisibilityCanBePrivate")
object ColumnValueConversions {
    const val epsilon = 0.000_000_1

    val operators = listOf(
        "plus", "minus", "times", "div",
        "eq", "ne")


    operator fun Int.plus(value: ColumnValue): ColumnValue {
        val numberValue = value.number
        if (numberValue.isNaN()) {
            return ColumnValue.ofTextNan(this.toString() + value.toString())
        }
        return ColumnValue.ofNumber(this + numberValue)
    }


    operator fun Double.plus(value: ColumnValue): ColumnValue {
        val numberValue = value.number
        if (numberValue.isNaN()) {
            return ColumnValue.ofTextNan(this.toString() + value.toString())
        }
        return ColumnValue.ofNumber(this + numberValue)
    }


    operator fun Int.times(value: ColumnValue): ColumnValue {
        val numberValue = value.number
        if (numberValue.isNaN()) {
            return ColumnValue.errorValue
        }
        return ColumnValue.ofNumber(this * numberValue)
    }


    operator fun Double.times(value: ColumnValue): ColumnValue {
        val numberValue = value.number
        if (numberValue.isNaN()) {
            return ColumnValue.errorValue
        }
        return ColumnValue.ofNumber(this * numberValue)
    }


    operator fun Int.div(value: ColumnValue): ColumnValue {
        val numberValue = value.number
        if (numberValue.isNaN()) {
            return ColumnValue.errorValue
        }
        return ColumnValue.ofNumber(this / numberValue)
    }


    operator fun Double.div(value: ColumnValue): ColumnValue {
        val numberValue = value.number
        if (numberValue.isNaN()) {
            return ColumnValue.errorValue
        }
        return ColumnValue.ofNumber(this / numberValue)
    }


    operator fun Int.minus(value: ColumnValue): ColumnValue {
        val numberValue = value.number
        if (numberValue.isNaN()) {
            return ColumnValue.errorValue
        }
        return ColumnValue.ofNumber(this - numberValue)
    }


    operator fun Double.minus(value: ColumnValue): ColumnValue {
        val numberValue = value.number
        if (numberValue.isNaN()) {
            return ColumnValue.errorValue
        }
        return ColumnValue.ofNumber(this - numberValue)
    }


    infix fun Int.eq(value: ColumnValue): Boolean {
        val number = value.number
        val asInt = number.toInt()
        val remainder = number - asInt
        if (remainder > epsilon) {
            return false
        }
        return this == asInt
    }


    infix fun Double.eq(value: ColumnValue): Boolean {
        return (this - value.number) < epsilon
    }


    infix fun String.eq(value: ColumnValue): Boolean {
        return this == value.text
    }


    infix fun Int.ne(value: ColumnValue): Boolean {
        return ! eq(value)
    }


    infix fun Double.ne(value: ColumnValue): Boolean {
        return ! eq(value)
    }
}