package tech.kzen.auto.server.paradigm.detached

import tech.kzen.auto.common.paradigm.common.model.ExecutionRequest
import tech.kzen.lib.common.model.locate.ObjectLocation


interface DetachedDownloadExecutor {
    suspend fun executeDownload(
            actionLocation: ObjectLocation,
            request: ExecutionRequest
    ): ExecutionDownloadResult
}
