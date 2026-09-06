package tech.kzen.auto.common.objects.document.plugin.model


/** A bundled notation document a scope shipped, with the exact resource it was read from. */
data class PluginDocumentDetail(
    val path: String,
    val origin: String
) {
    companion object {
        private const val pathKey = "path"
        private const val originKey = "origin"

        fun ofCollection(collection: Map<String, Any?>): PluginDocumentDetail {
            return PluginDocumentDetail(collection[pathKey] as String, collection[originKey] as String)
        }
    }

    fun asCollection(): Map<String, Any?> {
        return mapOf(pathKey to path, originKey to origin)
    }
}
