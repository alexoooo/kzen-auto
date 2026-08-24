package tech.kzen.auto.server.service.storage

import java.nio.file.Path


class SchemaCacheStorageArea(
    root: Path,
    private val anyRunActive: () -> Boolean,
    private val invalidate: (String) -> Unit
): DirectoryStorageArea(
    "index",
    "Input indexes",
    "Cached input schemas, rebuilt on demand.",
    root
) {
    override fun bundleActive(bundleKey: String): Boolean = anyRunActive()


    override fun deleteBundle(bundleKey: String): String? {
        if (bundleActive(bundleKey)) {
            return "In use: ${bundleDisplayName(bundleKey)}"
        }
        invalidate(bundleKey)
        return super.deleteBundle(bundleKey)
    }
}
