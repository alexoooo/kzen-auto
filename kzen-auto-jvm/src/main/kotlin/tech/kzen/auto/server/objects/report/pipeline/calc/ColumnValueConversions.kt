package tech.kzen.auto.server.objects.report.pipeline.calc


@Suppress("unused")
object ColumnValueConversions {
    val operators = listOf(
        "plus")


    operator fun Int.plus(value: ColumnValue): ColumnValue {
        val numberValue = value.toDoubleOrNan()
        if (numberValue.isNaN()) {
            return ColumnValue.ofTextNan(this.toString() + value.toString())
        }
        return ColumnValue.ofNumber(this + numberValue)
    }


    operator fun Double.plus(value: ColumnValue): ColumnValue {
        val numberValue = value.toDoubleOrNan()
        if (numberValue.isNaN()) {
            return ColumnValue.ofTextNan(this.toString() + value.toString())
        }
        return ColumnValue.ofNumber(this + numberValue)
    }
}