package tech.kzen.auto.server.objects.report.pivot.row.value

import tech.kzen.auto.server.objects.report.input.model.RecordTextFlyweight


interface RowValueIndex: AutoCloseable
{
    fun getOrAddIndex(value: String): Long
    fun getOrAddIndex(value: RecordTextFlyweight): Long

    fun getValue(valueIndex: Long): String
}