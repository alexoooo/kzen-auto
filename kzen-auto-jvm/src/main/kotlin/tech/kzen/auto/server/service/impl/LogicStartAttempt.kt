package tech.kzen.auto.server.service.impl

import tech.kzen.lib.common.exec.logic.run.model.LogicRunId


/**
 * The outcome of a start request. [Failed.reason] is served to the client as the 400 body, so a run that
 * can't start names WHY at the browser instead of only in the server log.
 */
sealed interface LogicStartAttempt {
    // For the callers that only need "did it start" — the LogicController contract, and tests driving a run.
    val runIdOrNull: LogicRunId?


    data class Started(
        val runId: LogicRunId
    ): LogicStartAttempt {
        override val runIdOrNull = runId
    }


    data class Failed(
        val reason: String
    ): LogicStartAttempt {
        override val runIdOrNull = null
    }
}
