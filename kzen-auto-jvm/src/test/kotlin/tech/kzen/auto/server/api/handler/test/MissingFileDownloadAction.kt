package tech.kzen.auto.server.api.handler.test

import tech.kzen.auto.server.paradigm.detached.DetachedDownloadAction
import tech.kzen.auto.server.paradigm.detached.ExecutionDownloadContent
import tech.kzen.auto.server.paradigm.detached.ExecutionDownloadResult
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.reflect.Reflect
import java.nio.file.Path


/**
 * Names a file nothing ever creates, so `GET /action/download` reaches the existence guard on the
 * [ExecutionDownloadContent.OfFile] branch. Declared in `main/` notation for the reason
 * [StreamedCsvDownloadAction] documents.
 */
@Reflect
class MissingFileDownloadAction: DetachedDownloadAction {
    @Suppress("ConstPropertyName")
    companion object {
        val absentPath: Path = Path.of("no-such-download-directory", "absent.csv")

        const val fileName = "MissingFileDownloadAction.csv"
    }


    override suspend fun executeDownload(request: ExecutionRequest): ExecutionDownloadResult {
        return ExecutionDownloadResult.ofFile(absentPath, fileName)
    }
}
