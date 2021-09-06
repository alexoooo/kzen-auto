package tech.kzen.auto.server.objects.pipeline.exec.output.pivot.row.value

import tech.kzen.auto.plugin.model.record.FlatFileRecordField
import tech.kzen.auto.server.objects.pipeline.exec.output.pivot.row.digest.DigestOrdinal


interface RowValueIndex: AutoCloseable
{
    fun size(): Long

    fun getOrAddIndex(value: String): DigestOrdinal
    fun getOrAddIndex(value: FlatFileRecordField): DigestOrdinal
    fun getOrAddIndexMissing(): DigestOrdinal

    fun getValue(valueIndex: Long): String?
}