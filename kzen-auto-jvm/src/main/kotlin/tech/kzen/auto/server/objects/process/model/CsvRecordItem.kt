package tech.kzen.auto.server.objects.process.model

import org.apache.commons.csv.CSVRecord


class CsvRecordItem(
    private val csvRecord: CSVRecord
): RecordItem {
    override fun getAll(columnNames: List<String>): List<String?> {
        val values = mutableListOf<String?>()
        for (columnName in columnNames) {
            values.add(get(columnName))
        }
        return values
    }

    override fun get(columnName: String): String? {
        if (! csvRecord.isMapped(columnName)) {
            return null
        }

        return csvRecord[columnName]
    }
}