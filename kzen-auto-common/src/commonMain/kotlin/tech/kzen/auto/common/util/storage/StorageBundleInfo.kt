package tech.kzen.auto.common.util.storage

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


// SER3: wire-only DTO (GET /storage/bundles -> ClientRestApi.storageBundleList). The hand-written
// toCollection()/ofCollection() pair and its five key constants were replaced by the generated codec; the
// @SerialName values preserve the original short wire keys. sizeBytes/lastModifiedMillis/active are now real
// JSON numbers/booleans (were stringly) — safe: same-release wire, and byte counts (~1e12) and epoch millis
// (~1.75e12) are bounded ~5000x below JS's 2^53 safe integer, so the Long-as-string convention
// (architecture.md §3) doesn't apply here.
@Serializable
data class StorageBundleInfo(
    val key: String,
    @SerialName("name") val displayName: String,
    @SerialName("size") val sizeBytes: Long,
    @SerialName("modified") val lastModifiedMillis: Long,
    val active: Boolean
)
