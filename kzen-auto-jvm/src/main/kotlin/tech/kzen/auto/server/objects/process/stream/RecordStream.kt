package tech.kzen.auto.server.objects.process.stream

import tech.kzen.auto.server.objects.process.model.RecordItem


interface RecordStream: Iterator<RecordItem>, AutoCloseable {
    fun header(): List<String>
}