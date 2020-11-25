package tech.kzen.auto.server.objects.report.stream

import org.apache.commons.csv.CSVParser
import tech.kzen.auto.server.objects.report.model.CsvRecordItem
import tech.kzen.auto.server.objects.report.model.RecordItem


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