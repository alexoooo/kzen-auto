package tech.kzen.auto.common.objects.document.report.output


data class OutputInfo(
    val runDir: String,
    val saveMessage: String,
    val modifiedTime: String?,
    val rowCount: Long,
    val preview: OutputPreview?,
    val status: OutputStatus
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val runDirKey = "work"
        private const val saveMessageKey = "message"
        private const val modifiedTimeKey = "modified"
        private const val previewKey = "preview"
        private const val countKey = "count"
        private const val statusKey = "status"


        fun fromCollection(collection: Map<String, Any?>): OutputInfo {
//            println("^^^ OutputInfo ## fromCollection - $collection")

            @Suppress("UNCHECKED_CAST")
            val outputPreviewCollection =
                collection[previewKey] as Map<String, Any>?

            val outputPreview =
                outputPreviewCollection
                    ?.let { OutputPreview.fromCollection(it) }

            val status = OutputStatus.valueOf(
                collection[statusKey] as String)

            return OutputInfo(
                collection[runDirKey] as String,
                collection[saveMessageKey] as String,
                collection[modifiedTimeKey] as String?,
                (collection[countKey] as String).toLong(),
                outputPreview,
                status)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun toCollection(): Map<String, Any?> {
        return mapOf(
            runDirKey to runDir,
            saveMessageKey to saveMessage,
            modifiedTimeKey to modifiedTime,
            countKey to rowCount.toString(),
            previewKey to preview?.toCollection(),
            statusKey to status.name)
    }
}