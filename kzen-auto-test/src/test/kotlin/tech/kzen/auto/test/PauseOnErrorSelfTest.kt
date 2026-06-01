package tech.kzen.auto.test

import org.junit.jupiter.api.Test


/**
 * Verifies per-run "pause on error": a Script started with pauseOnError=true whose step throws
 * PAUSES at that step instead of ending the run. The run stays active and reports state "Paused"
 * — a robust positive signal (plain failures, by contrast, just flip active to "null", which the
 * status endpoint can't distinguish from success; see this module's AGENTS.md).
 *
 * The fixture uses a single FormulaStep that throws, so it needs no browser and no SUT subprocess.
 */
class PauseOnErrorSelfTest: SelfTestBase() {
    @Test
    fun failingStepPausesInsteadOfEnding() {
        val runId = testerClient.startRun(
            documentPath = "test-suite/pause-on-error/FailingStep.yaml",
            objectPath = "main",
            pauseOnError = true)

        check(runId.isNotBlank()) { "expected a non-blank logic run id, got: '$runId'" }

        // awaitState returns only when active.state == "Paused" (else it throws with the last
        // status), so reaching past it is the core assertion: the thrown step paused the run.
        val status = testerClient.awaitState("Paused")

        @Suppress("UNCHECKED_CAST")
        val active = status["active"] as? Map<String, Any?>
        check(active != null) { "run should still be active (paused), got: $status" }
        check(active["state"] == "Paused") { "expected state Paused, got: ${active["state"]}" }

        // Stepping after an error-pause must be accepted — this validates the controller's
        // pause-invariant: an error-pause leaves the run in the same {paused, pauseRequested,
        // Pause-command} state as a user-initiated pause, so step()'s preconditions hold. Without
        // that fix the controller's check(pauseRequested) would fail here.
        val stepResponse = testerClient.step(runId)
        check(stepResponse == "Submitted") { "expected step to be Submitted, got: '$stepResponse'" }

        // It re-runs the still-failing step and pauses again.
        testerClient.awaitState("Paused")

        // Clean up the paused run so the tester teardown is tidy.
        testerClient.cancel(runId)
    }
}
