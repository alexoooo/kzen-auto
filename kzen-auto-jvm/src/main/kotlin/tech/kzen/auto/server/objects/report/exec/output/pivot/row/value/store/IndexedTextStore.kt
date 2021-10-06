package tech.kzen.auto.server.objects.report.exec.output.pivot.row.value.store

import tech.kzen.auto.plugin.model.record.FlatFileRecordField


interface IndexedTextStore: AutoCloseable {
    fun add(text: String)
    fun add(field: FlatFileRecordField)

    fun get(textOrdinal: Long): String
}