package tech.kzen.auto.client.objects.document.job.display

import tech.kzen.auto.common.objects.document.job.JobReadConventions


/** A column the files Parse sampled before Run hold, and which of them ([JobReadConventions.sampledColumnsKey]). */
data class SampledColumn(
    val name: String,
    val files: List<String>
) {
    /** The columns of [fileCount] sampled files. */
    data class Sampled(
        val columns: List<SampledColumn>,
        val fileCount: Int
    )


    companion object {
        /** The sampled columns in Parse's validation [details], or null when it reported none. */
        fun of(details: Map<String, Any?>): Sampled? {
            val columns = (details[JobReadConventions.sampledColumnsKey] as? List<*>)
                ?.mapNotNull { it as? Map<*, *> }
                ?.mapNotNull { column ->
                    val name = column[JobReadConventions.sampledColumnNameKey] as? String
                        ?: return@mapNotNull null
                    val files = (column[JobReadConventions.sampledColumnFilesKey] as? List<*>)
                        ?.mapNotNull { it as? String }
                        .orEmpty()
                    SampledColumn(name, files)
                }
                ?.takeIf { it.isNotEmpty() }
                ?: return null
            val fileCount = (details[JobReadConventions.sampledFilesKey] as? Number)?.toInt()
                ?: return null
            return Sampled(columns, fileCount)
        }
    }


    /** "In 2 of 3 files: a.csv, b.csv" */
    fun tooltip(fileCount: Int): String =
        "In ${files.size} of $fileCount files: ${files.joinToString(", ")}"
}
