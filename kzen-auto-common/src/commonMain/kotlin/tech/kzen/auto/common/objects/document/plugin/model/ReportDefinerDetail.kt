package tech.kzen.auto.common.objects.document.plugin.model

import tech.kzen.lib.platform.ClassName


data class ReportDefinerDetail(
    val coordinate: CommonPluginCoordinate,
    val extensions: List<String>,
    val dataEncoding: CommonDataEncodingSpec,
    val priority: Int,
    val modelType: ClassName
) {
    companion object {
        private const val nameKey = "name"
        private const val extensionsKey = "extensions"
        private const val dataEncodingKey = "dataEncoding"
        private const val priorityKey = "priority"
        private const val modelTypeKey = "modelType"

        @Suppress("UNCHECKED_CAST")
        fun ofCollection(collection: Map<String, Any?>): ReportDefinerDetail {
            return ReportDefinerDetail(
                CommonPluginCoordinate.ofString(collection[nameKey] as String),
                collection[extensionsKey] as List<String>,
                CommonDataEncodingSpec((collection[dataEncodingKey] as String?)?.let { CommonTextEncodingSpec(it) }),
                (collection[priorityKey] as String).toInt(),
                ClassName(collection[modelTypeKey] as String)
            )
        }
    }


    fun asCollection(): Map<String, Any?> {
        return mapOf(
            nameKey to coordinate.asString(),
            extensionsKey to extensions,
            dataEncodingKey to dataEncoding.textEncoding?.charsetName,
            priorityKey to priority.toString(),
            modelTypeKey to modelType.get()
        )
    }
}