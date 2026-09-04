package tech.kzen.auto.common.data.model

import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.ListExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.NullExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue


@Suppress("ConstPropertyName")
object DataModelKeys {
    const val attributes = "attributes"
    const val className = "class"
    const val diagnostics = "diagnostics"
    const val expectedFingerprint = "expectedFingerprint"
    const val generics = "generics"
    const val header = "header"
    const val id = "id"
    const val kind = "kind"
    const val manifest = "manifest"
    const val message = "message"
    const val nullable = "nullable"
    const val parts = "parts"
    const val ref = "ref"
    const val resolvedRead = "resolvedRead"
    const val resolutionDetails = "resolutionDetails"
    const val role = "role"
    const val source = "source"
    const val type = "type"
    const val units = "units"

    const val payloadKind = "payload"
    const val tabularKind = "tabular"
}


internal fun ExecutionValue.requiredModelMap(typeName: String): MapExecutionValue {
    return this as? MapExecutionValue
        ?: throw IllegalArgumentException("$typeName must be a map: $this")
}


internal fun MapExecutionValue.requiredValue(key: String): ExecutionValue {
    return values[key]
        ?: throw IllegalArgumentException("'$key' missing: $this")
}


internal fun MapExecutionValue.requiredMap(key: String): MapExecutionValue {
    return requiredValue(key) as? MapExecutionValue
        ?: throw IllegalArgumentException("'$key' must be a map: $this")
}


internal fun MapExecutionValue.requiredList(key: String): ListExecutionValue {
    return requiredValue(key) as? ListExecutionValue
        ?: throw IllegalArgumentException("'$key' must be a list: $this")
}


internal fun MapExecutionValue.optionalList(key: String): ListExecutionValue? {
    val value = values[key] ?: return null
    return value as? ListExecutionValue
        ?: throw IllegalArgumentException("'$key' must be a list: $this")
}


internal fun MapExecutionValue.requiredText(key: String): String {
    return (requiredValue(key) as? TextExecutionValue)?.value
        ?: throw IllegalArgumentException("'$key' must be text: $this")
}


internal fun MapExecutionValue.requiredNullableText(key: String): String? {
    return when (val value = requiredValue(key)) {
        NullExecutionValue -> null
        is TextExecutionValue -> value.value
        else -> throw IllegalArgumentException("'$key' must be text or null: $this")
    }
}


internal fun MapExecutionValue.requiredTextMap(key: String): LinkedHashMap<String, String> {
    val map = requiredMap(key)
    val decoded = linkedMapOf<String, String>()
    for ((entryKey, entryValue) in map.values) {
        val text = (entryValue as? TextExecutionValue)?.value
            ?: throw IllegalArgumentException("'$key.$entryKey' must be text: $this")
        decoded[entryKey] = text
    }
    return decoded
}


internal fun textExecutionMap(values: Map<String, String>): MapExecutionValue {
    val encoded = linkedMapOf<String, ExecutionValue>()
    for ((key, value) in values) {
        encoded[key] = TextExecutionValue(value)
    }
    return MapExecutionValue(encoded)
}
