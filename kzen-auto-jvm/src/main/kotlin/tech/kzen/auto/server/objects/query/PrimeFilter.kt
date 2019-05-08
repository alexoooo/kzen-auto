package tech.kzen.auto.server.objects.query

import tech.kzen.auto.common.paradigm.dataflow.api.StatelessDataflow
import tech.kzen.auto.common.paradigm.dataflow.api.input.RequiredInput
import tech.kzen.auto.common.paradigm.dataflow.api.output.OptionalOutput


@Suppress("unused")
class PrimeFilter(
        private val input: RequiredInput<Int>,
        private val output: OptionalOutput<Int>
): StatelessDataflow {
    override fun process() {
        val value = input.get()

        if (isPrime(value)) {
            output.set(value)
        }
    }


    private fun isPrime(n: Int): Boolean {
        if (n < 2) {
            return false
        }

        if (n == 2 || n == 3) {
            return true
        }

        if (n % 2 == 0 || n % 3 == 0) {
            return false
        }

        val sqrtN = Math.sqrt(n.toDouble()).toLong() + 1
        var i = 6
        while (i <= sqrtN) {
            if (n % (i - 1) == 0 || n % (i + 1) == 0) {
                return false
            }
            i += 6
        }

        return true
    }
}