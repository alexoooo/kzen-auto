package tech.kzen.auto.common.objects.document.process


data class OutputPreview(
    val header: List<String>,
    val rows: List<List<String>>
) {
    companion object {
        const val defaultRowCount = 100
//        const val defaultRowCount = 1_000

        private const val headerKey = "header"
        private const val rowsKey = "rows"


        @Suppress("UNCHECKED_CAST")
        fun fromCollection(collection: Map<String, Any>): OutputPreview {
            return OutputPreview(
                collection[headerKey] as List<String>,
                collection[rowsKey] as List<List<String>>)
        }
    }


    fun toCollection(): Map<String, Any> {
        return mapOf(
            headerKey to header,
            rowsKey to rows)
    }
}