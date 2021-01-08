package tech.kzen.auto.server.objects.report.pipeline.output.pivot.row.value

import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordTextFlyweight
import tech.kzen.auto.server.objects.report.pipeline.output.pivot.row.digest.DigestOrdinal


interface RowValueIndex: AutoCloseable
{
    fun size(): Long

    fun getOrAddIndex(value: String): DigestOrdinal
    fun getOrAddIndex(value: RecordTextFlyweight): DigestOrdinal
    fun getOrAddIndexMissing(): DigestOrdinal

    fun getValue(valueIndex: Long): String?
}