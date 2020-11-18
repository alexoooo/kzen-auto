package tech.kzen.auto.common.objects.document.process


data class OutputInfo(
//    val absolutePath: String,
    val saveMessage: String,
    val modifiedTime: String?,
    val rowCount: Long,
    val preview: OutputPreview?
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
//        private const val pathKey = "path"
        private const val saveMessageKey = "message"
        private const val modifiedTimeKey = "modified"
        private const val previewKey = "preview"
        private const val countKey = "count"


        fun fromCollection(collection: Map<String, Any?>): OutputInfo {
//            println("^^^ OutputInfo ## fromCollection - $collection")

            @Suppress("UNCHECKED_CAST")
            val outputPreviewCollection =
                collection[previewKey] as Map<String, Any>?

            val outputPreview =
                outputPreviewCollection
                    ?.let { OutputPreview.fromCollection(it) }

            return OutputInfo(
                collection[saveMessageKey] as String,
                collection[modifiedTimeKey] as String?,
                (collection[countKey] as String).toLong(),
                outputPreview)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun toCollection(): Map<String, Any?> {
        return mapOf(
//            pathKey to absolutePath,
            saveMessageKey to saveMessage,
            modifiedTimeKey to modifiedTime,
            countKey to rowCount.toString(),
            previewKey to preview?.toCollection())
    }
}