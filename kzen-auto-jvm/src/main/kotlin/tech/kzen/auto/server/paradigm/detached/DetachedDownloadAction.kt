package tech.kzen.auto.server.paradigm.detached

import tech.kzen.lib.common.exec.ExecutionRequest


interface DetachedDownloadAction {
    suspend fun executeDownload(
        request: ExecutionRequest
    ): ExecutionDownloadResult
}