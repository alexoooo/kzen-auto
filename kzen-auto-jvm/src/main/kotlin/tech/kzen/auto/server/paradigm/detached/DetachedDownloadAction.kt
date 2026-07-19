package tech.kzen.auto.server.paradigm.detached

import tech.kzen.lib.common.exec.ExecutionRequest


/**
 * Server-side Detached action that streams a download instead of returning an in-band result.
 *
 * Statelessness contract: implementations are instantiated from notation and may be cached and
 * reused across requests - including concurrent requests (see GraphInstanceCache). Instance fields
 * must be immutable configuration (notation-derived values, injected @Service references); all
 * per-request state belongs in locals. An implementation that cannot honour this opts out of reuse
 * by declaring `instanceCaching: "false"` on its archetype, which yields a fresh instance per request.
 */
interface DetachedDownloadAction {
    suspend fun executeDownload(
        request: ExecutionRequest
    ): ExecutionDownloadResult
}