package tech.kzen.auto.server.paradigm.detached

import tech.kzen.auto.common.paradigm.common.model.ExecutionRequest


interface DetachedDownloadAction {
    suspend fun executeDownload(
        request: ExecutionRequest
    ): ExecutionDownloadResult
}