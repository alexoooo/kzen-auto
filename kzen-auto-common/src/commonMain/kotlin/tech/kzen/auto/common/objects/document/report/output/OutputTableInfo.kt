package tech.kzen.auto.common.objects.document.report.output


data class OutputTableInfo(
    val saveMessage: String,
    val rowCount: Long,
    val preview: OutputPreview?
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val saveMessageKey = "message"
        private const val countKey = "count"
        private const val previewKey = "preview"


        fun fromCollection(collection: Map<String, Any?>): OutputTableInfo {
//            println("^^^ OutputInfo ## fromCollection - $collection")

            @Suppress("UNCHECKED_CAST")
            val outputPreviewCollection =
                collection[previewKey] as Map<String, Any>?

            val outputPreview =
                outputPreviewCollection
                    ?.let { OutputPreview.fromCollection(it) }

            return OutputTableInfo(
                collection[saveMessageKey] as String,
                (collection[countKey] as String).toLong(),
                outputPreview)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun toCollection(): Map<String, Any?> {
        return mapOf(
            saveMessageKey to saveMessage,
            countKey to rowCount.toString(),
            previewKey to preview?.toCollection())
    }
}