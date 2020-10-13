package tech.kzen.auto.server.objects.process.pivot.row.value


interface RowValueIndex: AutoCloseable
{
    fun getOrAddIndex(value: String): Long
    fun getValue(valueIndex: Long): String
}