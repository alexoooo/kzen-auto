package tech.kzen.auto.server.service.storage


/**
 * Snapshot of one deletable unit within a [ManagedStorageArea].
 */
data class StorageBundle(
    /** Directory name under the area root; opaque, stable across snapshots. */
    val key: String,

    /** Resolved human-readable name where possible, otherwise same as [key]. */
    val displayName: String,

    val sizeBytes: Long,

    /** Last-used signal driving LRU eviction order. */
    val lastModifiedMillis: Long,

    /** Currently in use by a live run; deletion is blocked while set. */
    val active: Boolean
)
