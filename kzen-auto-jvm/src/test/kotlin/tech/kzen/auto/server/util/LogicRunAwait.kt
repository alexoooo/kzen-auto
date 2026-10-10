package tech.kzen.auto.server.util

import tech.kzen.auto.server.service.impl.ServerLogicController
import tech.kzen.lib.common.exec.logic.run.model.LogicRunState
import java.util.concurrent.TimeUnit
import kotlin.test.fail


private const val pollIntervalMillis = 10L


/**
 * A control verb returns as soon as the engine accepts it, and the run then advances on the engine's own
 * threads — so any assertion about a run's state has to poll for it. Fails the test after [hangGuardMillis]
 * instead of hanging.
 */
fun awaitCondition(failureMessage: () -> String, condition: () -> Boolean) {
    val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(hangGuardMillis)
    while (!condition()) {
        if (System.nanoTime() >= deadlineNanos) {
            fail(failureMessage())
        }
        Thread.sleep(pollIntervalMillis)
    }
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
