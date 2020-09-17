package tech.kzen.auto.server.objects.process.model


interface RecordStream: Iterator<RecordItem>, AutoCloseable {
    fun header(): List<String>
}