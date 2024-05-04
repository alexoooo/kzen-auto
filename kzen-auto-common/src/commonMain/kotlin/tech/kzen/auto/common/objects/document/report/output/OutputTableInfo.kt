package tech.kzen.auto.common.objects.document.report.output


data class OutputTableInfo(
    val rowCount: Long,
    val preview: OutputPreview?
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val countKey = "count"
        private const val previewKey = "preview"


        fun fromCollection(collection: Map<String, Any?>): OutputTableInfo {
            @Suppress("UNCHECKED_CAST")
            val outputPreviewCollection =
                collection[previewKey] as Map<String, Any>?

            val outputPreview =
                outputPreviewCollection
                    ?.let { OutputPreview.ofCollection(it) }

            return OutputTableInfo(
                (collection[countKey] as String).toLong(),
                outputPreview)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun toCollection(): Map<String, Any?> {
        return mapOf(
            countKey to rowCount.toString(),
            previewKey to preview?.asCollection())
    }
}