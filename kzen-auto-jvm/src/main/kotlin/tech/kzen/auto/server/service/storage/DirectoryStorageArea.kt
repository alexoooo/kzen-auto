package tech.kzen.auto.server.service.storage

import tech.kzen.auto.server.util.WorkUtils
import java.nio.file.Files
import java.nio.file.Path


/**
 * [ManagedStorageArea] over a root directory whose immediate children (directories or files)
 * are the bundles. A missing root is an empty area (roots are created lazily by their owners).
 *
 * Subclasses customize per-bundle behaviour through the protected hooks; owners that must
 * release in-memory state before deletion (e.g. classloaders) override [deleteBundle].
 */
open class DirectoryStorageArea(
    override val id: String,
    override val displayName: String,
    override val description: String,
    protected val root: Path,
    override val deletable: Boolean = true,
    override val budgetBytes: Long? = null
): ManagedStorageArea {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun dirSize(dir: Path): Long {
            return Files.walk(dir).use { stream ->
                stream
                    .filter { Files.isRegularFile(it) }
                    .mapToLong { Files.size(it) }
                    .sum()
            }
        }


        fun dirLastModified(dir: Path): Long {
            return Files.walk(dir).use { stream ->
                stream
                    .mapToLong { Files.getLastModifiedTime(it).toMillis() }
                    .max()
                    .orElse(0)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun bundles(): List<StorageBundle> {
        if (!Files.isDirectory(root)) {
            return listOf()
        }

        val bundleDirs = Files.list(root).use { stream ->
            stream.toList()
        }

        return bundleDirs.map { bundleDir ->
            val key = bundleDir.fileName.toString()
            StorageBundle(
                key = key,
                displayName = bundleDisplayName(key),
                sizeBytes = dirSize(bundleDir),
                lastModifiedMillis = bundleLastModified(bundleDir),
                active = bundleActive(key))
        }
    }


    override fun deleteBundle(bundleKey: String): String? {
        if (!deletable) {
            return "Storage is managed automatically: $displayName"
        }

        val bundleDir = resolveBundleDir(bundleKey)
            ?: return "Unknown bundle: $bundleKey"

        if (bundleActive(bundleKey)) {
            return "In use: ${bundleDisplayName(bundleKey)}"
        }

        return try {
            WorkUtils.deleteDirThrowing(bundleDir)
            null
        }
        catch (e: Exception) {
            e.message ?: e.toString()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    protected open fun bundleDisplayName(bundleKey: String): String {
        return bundleKey
    }


    protected open fun bundleLastModified(bundleDir: Path): Long {
        return dirLastModified(bundleDir)
    }


    protected open fun bundleActive(bundleKey: String): Boolean {
        return false
    }


    /**
     * Null unless [bundleKey] is a plain child name of an existing bundle —
     * a key echoed from the client must not escape the area root.
     */
    protected fun resolveBundleDir(bundleKey: String): Path? {
        val bundleDir = root.resolve(bundleKey).normalize()

        if (bundleDir.parent != root.normalize() || !Files.exists(bundleDir)) {
            return null
        }
        return bundleDir
    }
}
