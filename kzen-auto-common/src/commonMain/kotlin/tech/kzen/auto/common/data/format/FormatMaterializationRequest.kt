package tech.kzen.auto.common.data.format

import tech.kzen.auto.common.data.read.ResolvedReadSpec
import tech.kzen.lib.common.exec.data.type.DataContract


data class FormatMaterializationRequest(
    val baseFormatReference: String,
    val resolvedRead: ResolvedReadSpec,
    val observedSchema: DataContract?,
    val overrides: Map<String, String?> = emptyMap()
) {
    init {
        require(baseFormatReference.isNotBlank()) { "Materialization base format must not be blank" }
    }
}
