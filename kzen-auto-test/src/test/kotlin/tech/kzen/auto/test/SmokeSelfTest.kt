package tech.kzen.auto.test

import org.junit.jupiter.api.Test


class SmokeSelfTest: SelfTestBase() {
    @Test
    fun openWelcome() {
        val runId = testerClient.startRun(
            documentPath = "test-suite/smoke/OpenWelcome.yaml",
            objectPath = "main")

        check(runId.isNotBlank()) { "expected a non-blank logic run id, got: '$runId'" }

        val finalStatus = testerClient.awaitCompletion(timeoutMs = 120_000)
        println("[smoke] final tester status: $finalStatus")
    }
}
