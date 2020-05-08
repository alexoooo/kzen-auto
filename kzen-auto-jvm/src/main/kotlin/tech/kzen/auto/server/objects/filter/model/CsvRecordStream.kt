package tech.kzen.auto.server.objects.filter.model

import org.apache.commons.csv.CSVParser


class CsvRecordStream(
    private val csvParser: CSVParser
):
    RecordStream
{
    override fun header(): List<String> {
        return csvParser.headerNames
    }


    override fun hasNext(): Boolean {
        return csvParser.iterator().hasNext()
    }


    override fun next(): RecordItem {
        return CsvRecordItem(
            csvParser.iterator().next())
    }


    override fun close() {
        csvParser.close()
    }
}