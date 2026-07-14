package tech.kzen.auto.test

import org.junit.jupiter.api.Test


class SmokeSelfTest: SelfTestBase() {
    @Test
    fun fizzBuzz() {
        val runId = testerClient.startRun(
            documentPath = "main/FizzBuzz/FizzBuzz.yaml",
            objectPath = "main")

        check(runId.isNotBlank()) { "expected a non-blank logic run id, got: '$runId'" }

        // Trace-based success check: fails if ANY step in the run errored — independent of pauseOnError.
        // (An `active == null` check alone can pass even when a step threw; see kzen-auto-test/AGENTS.md.)
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

        // The throwing Formula must be VALID before the run — its Nothing-typed expression compiles cleanly
        // and only fails when evaluated. A pre-run validation error (e.g. the inferred-Nothing compile error)
        // renders as a title-less "Error: ..." div the runtime-error XPath can't see, so it is asserted here
        // against the whole-page capture taken before Run was clicked.
        val preRun = testerClient.readDisplayedValue(
            documentPath = "main/FormulaError/Run and Read Error.yaml",
            objectPath = "main.steps/Read Before Run"
        ).trim()

        check(preRun.contains("Throwing Formula")) {
            "expected the pre-run capture to show the opened script, got: '$preRun'"
        }
        check(!preRun.contains("Error:")) {
            "expected no pre-run (validation) error, got: '$preRun'"
        }

        val observedError = testerClient.readDisplayedValue(
            documentPath = "main/FormulaError/Run and Read Error.yaml",
            objectPath = "main.steps/Read Error"
        ).trim()

        check(observedError.contains("intentional failure")) {
            "expected the SUT's formula error to be captured, got: '$observedError'"
        }
        check(!observedError.contains("Unable to compile")) {
            "expected a genuine run-time throw, not a compile failure (whose message quotes the generated " +
                    "source, making contains(\"intentional failure\") pass for the wrong reason), " +
                    "got: '$observedError'"
        }
    }
}
