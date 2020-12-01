package tech.kzen.auto.server.objects.report.pivot.row.value

import tech.kzen.auto.server.objects.report.input.model.RecordTextFlyweight
import tech.kzen.auto.server.objects.report.pivot.row.digest.DigestOrdinal


interface RowValueIndex: AutoCloseable
{
    fun size(): Long

    fun getOrAddIndex(value: String): DigestOrdinal
    fun getOrAddIndex(value: RecordTextFlyweight): DigestOrdinal
    fun getOrAddIndexMissing(): DigestOrdinal

    fun getValue(valueIndex: Long): String?
}