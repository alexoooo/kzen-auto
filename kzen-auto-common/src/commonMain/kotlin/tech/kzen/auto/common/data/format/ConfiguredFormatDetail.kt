package tech.kzen.auto.common.data.format


/**
 * One entry of the served format catalogue. [projectDocument] names the project document a format was defined in
 * (null for a built-in or plugin format); its [label] is then the format's own title or object name, never one
 * inherited from the format type, so it cannot pass for a built-in.
 */
data class ConfiguredFormatDetail(
    val reference: String,
    val label: String,
    val extensions: List<String>,
    val authoringCapabilityIdentity: String? = null,
    val overrideEditorReference: String? = null,
    val authoringAvailable: Boolean = false,
    val columnLockingAvailable: Boolean = false,
    val perFileOverrideAvailable: Boolean = true,
    val projectDocument: String? = null,
    val writable: Boolean = false
) {
    companion object {
        private const val referenceKey = "reference"
        private const val labelKey = "label"
        private const val extensionsKey = "extensions"
        private const val authoringCapabilityKey = "authoringCapability"
        private const val overrideEditorKey = "overrideEditor"
        private const val authoringAvailableKey = "authoringAvailable"
        private const val columnLockingAvailableKey = "columnLockingAvailable"
        private const val perFileOverrideAvailableKey = "perFileOverrideAvailable"
        private const val projectDocumentKey = "projectDocument"
        private const val writableKey = "writable"

        @Suppress("UNCHECKED_CAST")
        fun ofCollection(collection: Map<String, Any?>): ConfiguredFormatDetail = ConfiguredFormatDetail(
            collection[referenceKey] as String,
            collection[labelKey] as String,
            collection[extensionsKey] as List<String>,
            collection[authoringCapabilityKey] as? String,
            collection[overrideEditorKey] as? String,
            collection[authoringAvailableKey] as? Boolean ?: false,
            collection[columnLockingAvailableKey] as? Boolean ?: false,
            collection[perFileOverrideAvailableKey] as? Boolean ?: true,
            collection[projectDocumentKey] as? String,
            collection[writableKey] as? Boolean ?: false)
    }


    fun asCollection(): Map<String, Any?> = mapOf(
        referenceKey to reference,
        labelKey to label,
        extensionsKey to extensions,
        authoringCapabilityKey to authoringCapabilityIdentity,
        overrideEditorKey to overrideEditorReference,
        authoringAvailableKey to authoringAvailable,
        columnLockingAvailableKey to columnLockingAvailable,
        perFileOverrideAvailableKey to perFileOverrideAvailable,
        projectDocumentKey to projectDocument,
        writableKey to writable)
}
