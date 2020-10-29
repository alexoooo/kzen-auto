package tech.kzen.auto.common.objects.document.process


data class OutputInfo(
    val absolutePath: String,
    val modifiedTime: String?,
    val preview: OutputPreview?
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val pathKey = "path"
        private const val modifiedTimeKey = "modified"
        private const val previewKey = "preview"


        fun fromCollection(collection: Map<String, Any?>): OutputInfo {
            @Suppress("UNCHECKED_CAST")
            val outputPreviewCollection =
                collection[previewKey] as Map<String, Any>?

            val outputPreview =
                outputPreviewCollection
                    ?.let { OutputPreview.fromCollection(it) }

            return OutputInfo(
                collection[pathKey] as String,
                collection[modifiedTimeKey] as String?,
                outputPreview)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun toCollection(): Map<String, Any?> {
        return mapOf(
            pathKey to absolutePath,
            modifiedTimeKey to modifiedTime,
            previewKey to preview?.toCollection())
    }
}