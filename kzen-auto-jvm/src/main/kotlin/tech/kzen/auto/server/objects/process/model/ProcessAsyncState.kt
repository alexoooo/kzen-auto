package tech.kzen.auto.server.objects.process.model

import tech.kzen.auto.server.objects.process.filter.IndexedCsvTable
import tech.kzen.auto.server.objects.process.pivot.PivotBuilder
import java.nio.file.Path


data class ProcessAsyncState(
    val runDir: Path,
    val table: IndexedCsvTable?,
    val pivot: PivotBuilder?
): AutoCloseable {
    override fun close() {
        table?.close()
        pivot?.close()
    }
}