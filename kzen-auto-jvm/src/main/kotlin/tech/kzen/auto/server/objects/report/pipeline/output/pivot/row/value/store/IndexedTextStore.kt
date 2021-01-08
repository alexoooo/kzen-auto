package tech.kzen.auto.server.objects.report.pipeline.output.pivot.row.value.store

import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordTextFlyweight


interface IndexedTextStore: AutoCloseable {
    fun add(text: String)
    fun add(text: RecordTextFlyweight)

    fun get(textOrdinal: Long): String
}