package tech.kzen.auto.server.paradigm.detached

import java.nio.file.Path


/**
 * JVM-only by design: the client's download contract is a URL (ClientRestApi.linkDetachedDownload), so
 * nothing in commonMain or jsMain names this type and making it multiplatform would buy no reuse.
 * Reopening trigger: a commonMain object that needs to serve a download - kotlinx-io's RawSource is the
 * multiplatform equivalent of the JVM streaming in [ExecutionDownloadContent].
 */
data class ExecutionDownloadResult(
    val content: ExecutionDownloadContent,
    val fileName: String,
    val mimeType: String = mimeTypeCsv
) {
    @Suppress("ConstPropertyName")
    companion object {
        const val mimeTypeCsv = "text/csv"

        fun ofFile(
            path: Path,
            fileName: String,
            mimeType: String = mimeTypeCsv
        ): ExecutionDownloadResult {
            return ExecutionDownloadResult(
                ExecutionDownloadContent.OfFile(path), fileName, mimeType)
        }
    }
}
