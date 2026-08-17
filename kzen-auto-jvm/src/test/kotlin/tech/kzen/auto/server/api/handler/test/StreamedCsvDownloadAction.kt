package tech.kzen.auto.server.api.handler.test

import tech.kzen.auto.server.paradigm.detached.DetachedDownloadAction
import tech.kzen.auto.server.paradigm.detached.ExecutionDownloadContent
import tech.kzen.auto.server.paradigm.detached.ExecutionDownloadResult
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.reflect.Reflect


/**
 * Generates its whole body into the response stream, so `GET /action/download` reaches the
 * [ExecutionDownloadContent.OfWriter] branch. [tech.kzen.auto.server.api.handler.DetachedDownloadRouteTest]
 * declares it in a `main/` document inside a temporary module root: `test/` notation is outside
 * [tech.kzen.auto.common.util.AutoConventions.serverAllowed], so an action declared there is filtered out
 * before the detached executor can instantiate it.
 */
@Reflect
class StreamedCsvDownloadAction: DetachedDownloadAction {
    @Suppress("ConstPropertyName")
    companion object {
        const val payload = "city,amount\nMetropolis,42\nGotham,7\n"
        const val fileName = "StreamedCsvDownloadAction.csv"
    }


    override suspend fun executeDownload(request: ExecutionRequest): ExecutionDownloadResult {
        return ExecutionDownloadResult(
            ExecutionDownloadContent.OfWriter { it.write(payload.toByteArray()) },
            fileName)
    }
}
