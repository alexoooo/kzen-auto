package tech.kzen.auto.server.objects.report.pipeline.output.pivot.row.value.store

import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordFieldFlyweight


interface IndexedTextStore: AutoCloseable {
    fun add(text: String)
    fun add(field: RecordFieldFlyweight)

    fun get(textOrdinal: Long): String
}