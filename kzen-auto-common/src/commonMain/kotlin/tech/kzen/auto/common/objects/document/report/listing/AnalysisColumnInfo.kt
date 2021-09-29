package tech.kzen.auto.common.objects.document.report.listing


data class AnalysisColumnInfo(
    val inputColumns: Map<String, Boolean>,
    val allowPatternError: String?,
    val excludePatternError: String?
) {
    companion object {
        private const val inputColumnsKey = "columns"
        private const val allowPatternErrorKey = "allow-error"
        private const val excludePatternErrorKey = "exclude-error"

        fun ofCollection(collection: Map<String, Any>): AnalysisColumnInfo {
            val inputColumns = collection[inputColumnsKey] as Map<*, *>
            val inputColumnsSafeCast = inputColumns.map { (it.key as String) to (it.value as Boolean) }.toMap()

            return AnalysisColumnInfo(
                inputColumnsSafeCast,
                collection[allowPatternErrorKey] as String?,
                collection[excludePatternErrorKey] as String?
            )
        }
    }


    fun asCollection(): Map<String, Any> {
        val builder = mutableMapOf<String, Any>()

        builder[inputColumnsKey] = inputColumns

        if (allowPatternError != null) {
            builder[allowPatternErrorKey] = allowPatternError
        }

        if (excludePatternError != null) {
            builder[excludePatternErrorKey] = excludePatternError
        }

        return builder
    }
}