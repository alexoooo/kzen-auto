package tech.kzen.auto.server.objects.filter.model



class ListRecordItem(
    private val headerIndex: Map<String, Int>,
    private val values: List<String>
): RecordItem {
    override fun getAll(columnNames: List<String>): List<String?> {
        val builder = mutableListOf<String?>()
        for (columnName in columnNames) {
            builder.add(get(columnName))
        }
        return builder
    }


    override fun get(columnName: String): String? {
        val index = headerIndex[columnName]
            ?: return null

        return values[index]
    }
}