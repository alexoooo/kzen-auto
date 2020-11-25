package tech.kzen.auto.server.objects.report.model


interface RecordItem {
    fun columnNames(): List<String>

    fun getAll(columnNames: List<String>): List<String?>
    fun getOrEmptyAll(columnNames: List<String>): List<String>

    fun get(columnName: String): String?
}