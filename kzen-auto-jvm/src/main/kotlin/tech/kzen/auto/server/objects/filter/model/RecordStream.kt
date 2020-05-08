package tech.kzen.auto.server.objects.filter.model


interface RecordStream: Iterator<RecordItem>, AutoCloseable {
    fun header(): List<String>
}