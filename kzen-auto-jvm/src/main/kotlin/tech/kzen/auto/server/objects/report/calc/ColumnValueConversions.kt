package tech.kzen.auto.server.objects.report.calc


@Suppress("unused")
object ColumnValueConversions {
    val operators = listOf(
        "plus")


    operator fun Int.plus(value: ColumnValue): ColumnValue {
        val valueNumber = value.text.toIntOrNull()

        val additionText =
            if (valueNumber == null) {
                toString() + value
            }
            else {
                (this + valueNumber).toString()
            }

        return ColumnValue(additionText)
    }


    operator fun Double.plus(value: ColumnValue): ColumnValue {
        val valueNumber = value.text.toDoubleOrNull()

        val additionText =
            if (valueNumber == null) {
                toString() + value
            }
            else {
                (this + valueNumber).toString()
            }

        return ColumnValue(additionText)
    }
}