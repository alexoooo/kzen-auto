package tech.kzen.auto.server.objects.report.model

import org.apache.commons.csv.CSVRecord


class CsvRecordItem(
    private val csvRecord: CSVRecord
):
    RecordItem
{
    //-----------------------------------------------------------------------------------------------------------------
    private inner class CsvRecordList: AbstractList<String>() {
        override val size: Int
            get() = csvRecord.size()

        override fun get(index: Int): String {
            return csvRecord[index]
        }

        override fun iterator(): Iterator<String> {
            return csvRecord.iterator()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun columnNames(): List<String> {
        return csvRecord.parser.headerNames
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun getAll(columnNames: List<String>): List<String?> {
        if (csvRecord.parser.headerNames == columnNames) {
            return CsvRecordList()
        }

        val values = mutableListOf<String?>()
        for (columnName in columnNames) {
            values.add(get(columnName))
        }
        return values
    }


    override fun getOrEmptyAll(columnNames: List<String>): List<String> {
        if (csvRecord.parser.headerNames == columnNames) {
            return CsvRecordList()
        }

        val values = mutableListOf<String>()
        for (columnName in columnNames) {
            values.add(get(columnName) ?: "")
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