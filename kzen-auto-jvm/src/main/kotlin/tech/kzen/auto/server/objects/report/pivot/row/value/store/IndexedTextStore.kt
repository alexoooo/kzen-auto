package tech.kzen.auto.server.objects.report.pivot.row.value.store


interface IndexedTextStore: AutoCloseable {
    fun add(text: String)
    fun get(textOrdinal: Long): String
}