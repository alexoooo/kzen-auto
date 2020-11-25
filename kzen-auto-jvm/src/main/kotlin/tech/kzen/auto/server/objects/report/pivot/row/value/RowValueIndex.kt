package tech.kzen.auto.server.objects.report.pivot.row.value


interface RowValueIndex: AutoCloseable
{
    fun getOrAddIndex(value: String): Long
    fun getValue(valueIndex: Long): String
}