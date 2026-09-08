package tech.kzen.auto.common.objects.document.job.preview

/** Column identity includes occurrence and distinguishes the root value from a field named value. */
class PreviewTable(items: List<PreviewNode>) {
    data class Column(val name: String, val occurrence: Int = 0, val root: Boolean = false) {
        val label: String get() = name + if (occurrence <= 0) "" else " (#${occurrence + 1})"
    }

    val columns: List<Column> = items.flatMap { item ->
        if (item.kind == "record" && item.children.isNotEmpty()) item.children.map { Column(it.name, it.occurrence) }
        else listOf(Column("value", root = true))
    }.distinct().let { all ->
        if (all.size <= maximumColumns) all
        else all.take(maximumColumns - 1) + Column("Item details", root = true)
    }

    val rows: List<List<PreviewNode>> = items.map { item ->
        columns.map { column ->
            if (column.root) {
                if (item.kind != "record" || item.children.isEmpty() || column.name == "Item details") item else absent
            }
            else if (item.kind == "record") {
                item.children.firstOrNull { it.name == column.name && it.occurrence == column.occurrence } ?: absent
            }
            else absent
        }
    }

    companion object {
        private const val maximumColumns = 200
        private val absent = PreviewNode("absent", "Absent")
    }
}
