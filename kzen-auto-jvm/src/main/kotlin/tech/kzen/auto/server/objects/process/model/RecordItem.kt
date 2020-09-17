package tech.kzen.auto.server.objects.process.model


interface RecordItem {
    fun getAll(columnNames: List<String>): List<String?>
    fun get(columnName: String): String?
}