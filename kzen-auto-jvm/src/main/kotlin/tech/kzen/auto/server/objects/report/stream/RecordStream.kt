package tech.kzen.auto.server.objects.report.stream

import tech.kzen.auto.server.objects.report.model.RecordItem


interface RecordStream: Iterator<RecordItem>, AutoCloseable {
    fun header(): List<String>
}