package tech.kzen.auto.server.service.storage

import java.nio.file.Path


/**
 * Cached column listings of report input files (see
 * [tech.kzen.auto.server.objects.report.service.FilterIndex]), rebuilt on demand.
 */
class FilterIndexStorageArea(
    root: Path,
    private val anyRunActive: () -> Boolean
): DirectoryStorageArea(
    "index",
    "Input indexes",
    "Cached column listings of report and job input files, rebuilt on demand.",
    root
) {
    // Coarse guard: any active logic run may be reading an index entry.
    override fun bundleActive(bundleKey: String): Boolean {
        return anyRunActive()
    }
}
