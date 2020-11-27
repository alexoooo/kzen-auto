package tech.kzen.auto.server.paradigm.detached

import tech.kzen.auto.common.paradigm.detached.model.DetachedRequest


interface DetachedDownloadAction {
    suspend fun executeDownload(
        request: DetachedRequest
    ): ExecutionDownloadResult
}