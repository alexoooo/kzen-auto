package tech.kzen.auto.common.util.storage

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


// SER3: wire-only DTO (GET /storage/summary -> ClientRestApi.storageSummary). The hand-written
// toCollection()/ofCollection() pair and its seven key constants were replaced by the generated codec; the
// @SerialName values preserve the original short wire keys. sizeBytes/bundleCount/deletable are now real JSON
// numbers/booleans (were stringly) — safe: same-release wire, and byte counts are bounded far below JS's 2^53
// safe integer, so the Long-as-string convention (architecture.md §3) doesn't apply here.
@Serializable
data class StorageAreaInfo(
    val id: String,
    @SerialName("name") val displayName: String,
    val description: String,
    @SerialName("size") val sizeBytes: Long,
    @SerialName("bundles") val bundleCount: Int,
    val deletable: Boolean
)
