package tech.kzen.auto.server.service.storage


/**
 * One registered on-disk storage area (a root directory the server owns), exposed to the
 * storage-management screen and to [StorageLruEvictor].
 *
 * A bundle is the smallest independently deletable unit within the area — typically one
 * child directory (a compiled-code signature, a report run, an input index entry).
 */
interface ManagedStorageArea {
    val id: String
    val displayName: String
    val description: String

    /** False for display-only areas whose lifecycle is managed elsewhere (logs, Job scratch). */
    val deletable: Boolean

    /** Non-null enables LRU eviction to stay under this many bytes (see [StorageLruEvictor]). */
    val budgetBytes: Long?


    fun bundles(): List<StorageBundle>

    /**
     * Releases any in-memory state referencing the bundle (classloaders, open files) FIRST,
     * then deletes it from disk. Refuses when the bundle is [StorageBundle.active].
     *
     * @return null on success, human-readable error otherwise
     */
    fun deleteBundle(bundleKey: String): String?
}
