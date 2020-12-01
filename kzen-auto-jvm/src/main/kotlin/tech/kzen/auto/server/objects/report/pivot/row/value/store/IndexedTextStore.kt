package tech.kzen.auto.server.objects.report.pivot.row.value.store

import tech.kzen.auto.server.objects.report.input.model.RecordTextFlyweight


interface IndexedTextStore: AutoCloseable {
    fun add(text: String)
    fun add(text: RecordTextFlyweight)

    fun get(textOrdinal: Long): String
}