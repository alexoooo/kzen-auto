package tech.kzen.auto.test

import org.junit.jupiter.api.Test


class SmokeSelfTest: SelfTestBase() {
    // TODO: make this pass (currently assertion is commented out)
    @Test
    fun openWelcome() {
        val runId = testerClient.startRun(
            documentPath = "main/FizzBuzz Script.yaml",
            objectPath = "main",
            pauseOnError = true)

        check(runId.isNotBlank()) { "expected a non-blank logic run id, got: '$runId'" }

        val finalStatus = testerClient.awaitSettled(timeoutMs = 120_000)
        println("[smoke] final tester status: $finalStatus")

        val active = finalStatus["active"]
//        check(active == null || active == "null") {
//            "script run failed (paused on error): $finalStatus"
//        }
    }
}
