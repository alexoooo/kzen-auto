package tech.kzen.auto.server.objects.filter.model


interface RecordItem {
    fun getAll(columnNames: List<String>): List<String?>
    fun get(columnName: String): String?
}