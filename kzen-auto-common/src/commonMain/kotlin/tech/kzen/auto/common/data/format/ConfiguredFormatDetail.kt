package tech.kzen.auto.common.data.format


data class ConfiguredFormatDetail(
    val reference: String,
    val label: String,
    val extensions: List<String>,
    val authoringCapabilityIdentity: String? = null,
    val overrideEditorReference: String? = null,
    val authoringAvailable: Boolean = false,
    val columnLockingAvailable: Boolean = false,
    val perFileOverrideAvailable: Boolean = true
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

        @Suppress("UNCHECKED_CAST")
        fun ofCollection(collection: Map<String, Any?>): ConfiguredFormatDetail = ConfiguredFormatDetail(
            collection[referenceKey] as String,
            collection[labelKey] as String,
            collection[extensionsKey] as List<String>,
            collection[authoringCapabilityKey] as? String,
            collection[overrideEditorKey] as? String,
            collection[authoringAvailableKey] as? Boolean ?: false,
            collection[columnLockingAvailableKey] as? Boolean ?: false,
            collection[perFileOverrideAvailableKey] as? Boolean ?: true)
    }


    fun asCollection(): Map<String, Any?> = mapOf(
        referenceKey to reference,
        labelKey to label,
        extensionsKey to extensions,
        authoringCapabilityKey to authoringCapabilityIdentity,
        overrideEditorKey to overrideEditorReference,
        authoringAvailableKey to authoringAvailable,
        columnLockingAvailableKey to columnLockingAvailable,
        perFileOverrideAvailableKey to perFileOverrideAvailable)
}
