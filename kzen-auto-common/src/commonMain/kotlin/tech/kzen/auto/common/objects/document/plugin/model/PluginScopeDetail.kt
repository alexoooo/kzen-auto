package tech.kzen.auto.common.objects.document.plugin.model


/**
 * One installed plugin scope as the Plugin document shows it: the scope row (id, version, SPI, directory or the
 * application classpath, load status) and, under it, only what was discovered through an explicit protocol
 * (readers, bundled documents with their exact origin, generated reflection modules) plus the `@Reflect`
 * classes this workspace has actually been asked to resolve, with their availability here, and every named
 * failure. Deliberately not a class inventory: an unreferenced class does not appear. Wire form is the
 * string/list/map collection the detached action returns.
 */
data class PluginScopeDetail(
    val id: String,
    val version: String?,
    val spiVersion: String?,
    val directory: String?,
    val jars: List<String>,
    val loaded: Boolean,
    val failure: String?,
    val readers: List<String>,
    val documents: List<PluginDocumentDetail>,
    val generatedModules: List<String>,
    val classes: List<PluginClassDetail>,
    val shadowedClasses: List<String>,
    val ambiguousClasses: List<String>,
    val failures: List<String>
) {
    companion object {
        private const val idKey = "id"
        private const val versionKey = "version"
        private const val spiVersionKey = "spi"
        private const val directoryKey = "directory"
        private const val jarsKey = "jars"
        private const val loadedKey = "loaded"
        private const val failureKey = "failure"
        private const val readersKey = "readers"
        private const val documentsKey = "documents"
        private const val generatedModulesKey = "generatedModules"
        private const val classesKey = "classes"
        private const val shadowedClassesKey = "shadowedClasses"
        private const val ambiguousClassesKey = "ambiguousClasses"
        private const val failuresKey = "failures"

        @Suppress("UNCHECKED_CAST")
        fun ofCollection(collection: Map<String, Any?>): PluginScopeDetail {
            return PluginScopeDetail(
                collection[idKey] as String,
                collection[versionKey] as String?,
                collection[spiVersionKey] as String?,
                collection[directoryKey] as String?,
                collection[jarsKey] as List<String>,
                collection[loadedKey] as Boolean,
                collection[failureKey] as String?,
                collection[readersKey] as List<String>,
                (collection[documentsKey] as List<Map<String, Any?>>).map { PluginDocumentDetail.ofCollection(it) },
                collection[generatedModulesKey] as List<String>,
                (collection[classesKey] as List<Map<String, Any?>>).map { PluginClassDetail.ofCollection(it) },
                collection[shadowedClassesKey] as List<String>,
                collection[ambiguousClassesKey] as List<String>,
                collection[failuresKey] as List<String>)
        }
    }


    val isApplication: Boolean
        get() = directory == null


    fun asCollection(): Map<String, Any?> {
        return mapOf(
            idKey to id,
            versionKey to version,
            spiVersionKey to spiVersion,
            directoryKey to directory,
            jarsKey to jars,
            loadedKey to loaded,
            failureKey to failure,
            readersKey to readers,
            documentsKey to documents.map { it.asCollection() },
            generatedModulesKey to generatedModules,
            classesKey to classes.map { it.asCollection() },
            shadowedClassesKey to shadowedClasses,
            ambiguousClassesKey to ambiguousClasses,
            failuresKey to failures)
    }
}
