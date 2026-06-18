package tech.kzen.auto.test

import org.junit.jupiter.api.Test


class SmokeSelfTest: SelfTestBase() {
    @Test
    fun openWelcome() {
        val runId = testerClient.startRun(
            documentPath = "main/FizzBuzz/FizzBuzz.yaml",
            objectPath = "main")

        check(runId.isNotBlank()) { "expected a non-blank logic run id, got: '$runId'" }

        // Trace-based success check: fails if ANY step in the run errored — independent of pauseOnError.
        // (The old `active == null` check could pass even when a step threw; see kzen-auto-test/AGENTS.md.)
        testerClient.awaitSuccess("main/FizzBuzz/FizzBuzz.yaml")

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


    /**
     * Negative control for failure detection: a SUT Script whose Formula throws must be identified as a
     * failure, not a silent pass. The orchestration runs the preloaded throwing Script in the SUT and
     * reads the rendered error via the browser. The orchestration itself runs clean (awaitSuccess), and
     * we assert it captured the expected formula error — proving the harness correctly surfaces failures.
     */
    @Test
    fun formulaErrorIsDetected() {
        val runId = testerClient.startRun(
            documentPath = "main/FormulaError/FormulaError.yaml",
            objectPath = "main")

        check(runId.isNotBlank()) { "expected a non-blank logic run id, got: '$runId'" }

        testerClient.awaitSuccess("main/FormulaError/FormulaError.yaml")

        val observedError = testerClient.readDisplayedValue(
            documentPath = "main/FormulaError/Run and Read Error.yaml",
            objectPath = "main.steps/Read Error"
        ).trim()

        check(observedError.contains("intentional failure")) {
            "expected the SUT's formula error to be captured, got: '$observedError'"
        }
    }
}
