package tech.kzen.auto.common.paradigm.detached

import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult


/**
 * One-shot request/response action (Detached paradigm): executes one [ExecutionRequest] and returns
 * synchronously, with no state tracked between requests.
 *
 * Statelessness contract: implementations are instantiated from notation and may be cached and
 * reused across requests - including concurrent requests (see the server's GraphInstanceCache).
 * Instance fields must be immutable configuration (notation-derived values, injected @Service
 * references); all per-request state belongs in locals. An implementation that cannot honour this
 * opts out of reuse by declaring `instanceCaching: "false"` on its archetype, which yields a fresh
 * instance per request.
 */
interface DetachedAction {
    suspend fun execute(
        request: ExecutionRequest
    ): ExecutionResult
}