package tech.kzen.auto.server.objects.report.exec.calc


@Suppress("unused", "MemberVisibilityCanBePrivate")
object ColumnValueConversions {
    val operators = listOf(
        "plus", "minus", "times", "div", "compareTo", "eq", "ne", "If")


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
        return value eq this
    }


    infix fun Double.eq(value: ColumnValue): Boolean {
        return value eq this
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


    operator fun Int.compareTo(columnValue: ColumnValue): Int {
        return -columnValue.compareTo(this)
    }


    operator fun Double.compareTo(columnValue: ColumnValue): Int {
        return -columnValue.compareTo(this)
    }


    operator fun String.compareTo(columnValue: ColumnValue): Int {
        return -columnValue.compareTo(this)
    }


    @Suppress("FunctionName")
    fun If(condition: Any?, trueValue: Any?, falseValue: Any?): ColumnValue {
        val value =
            if (ColumnValue.ofScalar(condition).truthy) {
                trueValue
            }
            else {
                falseValue
            }

        return ColumnValue.ofScalar(value)
    }


    @Suppress("FunctionName")
    fun ColumnValue.If(trueValue: Any?, falseValue: Any?): ColumnValue {
        val value =
            if (truthy) {
                trueValue
            }
            else {
                falseValue
            }

        return ColumnValue.ofScalar(value)
    }
}