package tech.kzen.auto.common.data.format


data class ConfiguredFormatDetail(
    val reference: String,
    val label: String,
    val extensions: List<String>
) {
    companion object {
        private const val referenceKey = "reference"
        private const val labelKey = "label"
        private const val extensionsKey = "extensions"

        @Suppress("UNCHECKED_CAST")
        fun ofCollection(collection: Map<String, Any?>): ConfiguredFormatDetail = ConfiguredFormatDetail(
            collection[referenceKey] as String,
            collection[labelKey] as String,
            collection[extensionsKey] as List<String>)
    }


    fun asCollection(): Map<String, Any?> = mapOf(
        referenceKey to reference,
        labelKey to label,
        extensionsKey to extensions)
}
