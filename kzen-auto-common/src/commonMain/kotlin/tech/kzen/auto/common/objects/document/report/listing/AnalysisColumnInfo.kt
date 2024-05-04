package tech.kzen.auto.common.objects.document.report.listing


data class AnalysisColumnInfo(
    val inputColumns: FilteredHeaderListing,
    val calculatedColumns: FilteredHeaderListing,
    val allowPatternError: String?,
    val excludePatternError: String?
) {
    //-----------------------------------------------------------------------------------------------------------------
    @Suppress("ConstPropertyName", "RedundantSuppression")
    companion object {
        private const val inputColumnsKey = "input-columns"
        private const val calculatedColumnsKey = "calculated-columns"
        private const val allowPatternErrorKey = "allow-error"
        private const val excludePatternErrorKey = "exclude-error"

        fun ofCollection(collection: Map<String, Any>): AnalysisColumnInfo {
            val inputColumnsCollection = (collection[inputColumnsKey] as List<*>).map { it as String }
            val inputColumns = FilteredHeaderListing.ofCollection(inputColumnsCollection)

            val calculatedColumnsCollection = (collection[calculatedColumnsKey] as List<*>).map { it as String }
            val calculatedColumns = FilteredHeaderListing.ofCollection(calculatedColumnsCollection)

            return AnalysisColumnInfo(
                inputColumns,
                calculatedColumns,
                collection[allowPatternErrorKey] as String?,
                collection[excludePatternErrorKey] as String?
            )
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    val inputAndCalculatedColumns: FilteredHeaderListing by lazy {
        inputColumns.append(calculatedColumns)
    }

    val filteredInputAndCalculatedColumns: HeaderListing by lazy {
        inputAndCalculatedColumns.includedHeaderListing()
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun asCollection(): Map<String, Any> {
        val builder = mutableMapOf<String, Any>()

        builder[inputColumnsKey] = inputColumns.asCollection()
        builder[calculatedColumnsKey] = calculatedColumns.asCollection()

        if (allowPatternError != null) {
            builder[allowPatternErrorKey] = allowPatternError
        }

        if (excludePatternError != null) {
            builder[excludePatternErrorKey] = excludePatternError
        }

        return builder
    }
}