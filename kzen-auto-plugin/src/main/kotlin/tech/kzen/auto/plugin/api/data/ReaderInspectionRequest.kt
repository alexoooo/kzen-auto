package tech.kzen.auto.plugin.api.data


data class ReaderInspectionRequest(
    val open: ReaderOpenRequest,
    val maximumRecords: Long
) {
    init {
        require(maximumRecords > 0) { "Inspection record limit must be positive" }
    }
}
