package tech.kzen.auto.test

import org.junit.jupiter.api.Test


class SmokeSelfTest: SelfTestBase() {
    @Test
    fun openWelcome() {
        val runId = testerClient.startRun(
            documentPath = "main/FizzBuzz/FizzBuzz.yaml",
            objectPath = "main",
            pauseOnError = true)

        check(runId.isNotBlank()) { "expected a non-blank logic run id, got: '$runId'" }

        val finalStatus = testerClient.awaitSettled(timeoutMs = 120_000)
        println("[smoke] final tester status: $finalStatus")

        val active = finalStatus["active"]
        check(active == null || active == "null") {
            "script run failed (paused on error): $finalStatus"
        }

        val expected = (1..100).joinToString(prefix = "[", postfix = "]", separator = ", ") { n ->
            when {
                n % 3 == 0 && n % 5 == 0 -> "fizzbuzz"
                n % 3 == 0 -> "fizz"
                n % 5 == 0 -> "buzz"
                else -> n.toString()
            }
        }

        val actual = testerClient.readDisplayedValue(
            documentPath = "main/FizzBuzz/Run/Run and Await.yaml",
            objectPath = "main.steps/Read Display"
        ).trim()

        check(actual == expected) {
            "FizzBuzz output mismatch:\n  expected: $expected\n  actual:   $actual"
        }
    }
}
