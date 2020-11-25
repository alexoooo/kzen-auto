package tech.kzen.auto.server.objects.report.model



class ListRecordItem(
    private val headerNames: List<String>,
    private val headerIndex: Map<String, Int>,
    private val values: List<String>
):
    RecordItem
{
    override fun columnNames(): List<String> {
        return headerNames
    }


    override fun getAll(columnNames: List<String>): List<String?> {
        if (headerNames == columnNames) {
            return values
        }

        val builder = mutableListOf<String?>()
        for (columnName in columnNames) {
            builder.add(get(columnName))
        }
        return builder
    }


    override fun getOrEmptyAll(columnNames: List<String>): List<String> {
        if (headerNames == columnNames) {
            return values
        }

        val builder = mutableListOf<String>()
        for (columnName in columnNames) {
            builder.add(get(columnName) ?: "")
        }
        return builder
    }


    override fun get(columnName: String): String? {
        val index = headerIndex[columnName]
            ?: return null

        return values[index]
    }
}