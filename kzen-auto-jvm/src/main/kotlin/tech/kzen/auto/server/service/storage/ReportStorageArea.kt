package tech.kzen.auto.server.service.storage

import tech.kzen.auto.common.objects.document.report.output.OutputStatus
import tech.kzen.auto.server.objects.report.service.ReportWorkPool
import java.nio.file.Path


/**
 * Report run dirs under the default report work root. Runs written to a report's custom
 * (absolute) work path resolve outside this root and are user-owned output, not managed cache.
 */
class ReportStorageArea(
    root: Path,
    private val reportWorkPool: ReportWorkPool
): DirectoryStorageArea(
    "report",
    "Report runs",
    "Results of report runs, kept after the run for browsing and download. " +
        "Re-running the report regenerates a deleted result.",
    root
) {
    override fun bundleActive(bundleKey: String): Boolean {
        return try {
            reportWorkPool.readRunStatus(root.resolve(bundleKey)) == OutputStatus.Running
        }
        catch (e: Exception) {
            // A corrupt run-info file must not block listing — deletion is its remedy.
            false
        }
    }
}
