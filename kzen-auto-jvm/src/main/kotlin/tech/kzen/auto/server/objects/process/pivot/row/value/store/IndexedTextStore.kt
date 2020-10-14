package tech.kzen.auto.server.objects.process.pivot.row.value.store


interface IndexedTextStore: AutoCloseable {
    fun add(text: String)
    fun get(textIndex: Long): String
}