package tech.kzen.auto.server.objects.filter.model

import java.io.BufferedReader


class TsvRecordStream(
    private val reader: BufferedReader
): RecordStream {
    companion object {
        private const val delimiter = '\t'
    }


    private val header: List<String>
    private val headerIndex: Map<String, Int>

    private var nextLine: String?


    init {
        val headerLine = reader.readLine()
        header = headerLine.split(delimiter)
        headerIndex = header.withIndex().map { it.value to it.index }.toMap()

        nextLine = reader.readLine()
    }


    override fun header(): List<String> {
        return header
    }


    override fun hasNext(): Boolean {
        return nextLine != null
    }


    override fun next(): RecordItem {
        val line = nextLine!!
        nextLine = reader.readLine()
        val values = line.split(delimiter)
        return ListRecordItem(headerIndex, values)
    }


    override fun close() {
        reader.close()
    }
}