package tech.kzen.auto.server.util

import tech.kzen.auto.server.service.impl.ServerLogicController
import tech.kzen.lib.common.exec.logic.run.model.LogicRunState
import kotlin.test.fail


private const val pollIntervalMillis = 10L
private const val pollAttempts = 500


/**
 * A control verb returns as soon as the engine accepts it, and the run then advances on the engine's own
 * threads — so any assertion about a run's state has to poll for it. Fails the test after
 * [pollAttempts] × [pollIntervalMillis] (5 seconds) instead of hanging.
 */
fun awaitCondition(failureMessage: () -> String, condition: () -> Boolean) {
    repeat(pollAttempts) {
        if (condition()) {
            return
        }
        Thread.sleep(pollIntervalMillis)
    }
    fail(failureMessage())
}


fun ServerLogicController.awaitState(state: LogicRunState) {
    awaitCondition({ "Run did not reach $state (was ${status().active?.state})" }) {
        status().active?.state == state
    }
}


fun ServerLogicController.awaitDone(failureMessage: String = "Run did not complete") {
    awaitCondition({ failureMessage }) {
        status().active == null
    }
}


/** Still active, but parked in one of the settled (non-executing) pause states. */
fun ServerLogicController.awaitSettled(failureMessage: String = "Run did not settle") {
    awaitCondition({ failureMessage }) {
        val state = status().active?.state
        state != null && !state.isExecuting()
    }
}
