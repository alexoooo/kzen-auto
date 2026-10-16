package tech.kzen.auto.client.objects.document.job.display

import tech.kzen.auto.common.objects.document.job.JobReadConventions


/** A column the files Parse read before Run hold, and which of them ([JobReadConventions.columnsKey]). */
data class InferredColumn(
    val name: String,
    val files: List<String>
) {
    /** The columns of [fileCount] files. */
    data class Inferred(
        val columns: List<InferredColumn>,
        val fileCount: Int
    )


    companion object {
        /** The inferred columns in Parse's validation [details], or null when it reported none. */
        fun of(details: Map<String, Any?>): Inferred? {
            val columns = (details[JobReadConventions.columnsKey] as? List<*>)
                ?.mapNotNull { it as? Map<*, *> }
                ?.mapNotNull { column ->
                    val name = column[JobReadConventions.columnNameKey] as? String
                        ?: return@mapNotNull null
                    val files = (column[JobReadConventions.columnFilesKey] as? List<*>)
                        ?.mapNotNull { it as? String }
                        .orEmpty()
                    InferredColumn(name, files)
                }
                ?.takeIf { it.isNotEmpty() }
                ?: return null
            val fileCount = (details[JobReadConventions.inspectedFilesKey] as? Number)?.toInt()
                ?: return null
            return Inferred(columns, fileCount)
        }
    }


    /** "In 2 of 3 files: a.csv, b.csv" */
    fun tooltip(fileCount: Int): String =
        "In ${files.size} of $fileCount files: ${files.joinToString(", ")}"
}
