package tech.kzen.auto.client.objects.document.common.valid

import tech.kzen.auto.client.util.async
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.NullExecutionValue
import kotlin.test.Test
import kotlin.test.assertEquals


class ServerValidationFetchTest {
    //-----------------------------------------------------------------------------------------------------------------
    private fun value(marker: String): MapExecutionValue {
        return MapExecutionValue(mapOf(marker to NullExecutionValue))
    }


    private class CountingPerform(private val response: ExecutionResult) {
        var performCount = 0
            private set

        suspend fun perform(): ExecutionResult {
            performCount++
            return response
        }
    }


    private suspend fun fetch(
        counting: CountingPerform,
        isCurrent: () -> Boolean = { true }
    ): ServerValidationFetch.Outcome<MapExecutionValue> {
        return ServerValidationFetch.fetch(
            isCurrent = isCurrent,
            perform = { counting.perform() },
            parse = { it })
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun currentAnswerApplies() = async {
        val counting = CountingPerform(ExecutionSuccess.ofValue(value("current")))

        val outcome = fetch(counting)

        assertEquals(ServerValidationFetch.Outcome.Current(value("current")), outcome)
        assertEquals(1, counting.performCount)
    }


    @Test
    fun supersededBeforeSendingNeverAsks() = async {
        val counting = CountingPerform(ExecutionSuccess.ofValue(value("current")))

        val outcome = fetch(counting) { false }

        assertEquals(ServerValidationFetch.Outcome.Superseded, outcome)
        assertEquals(0, counting.performCount)
    }


    @Test
    fun answerSupersededWhileInFlightIsDropped() = async {
        val counting = CountingPerform(ExecutionSuccess.ofValue(value("stale")))
        var checks = 0

        val outcome = fetch(counting) { checks++ == 0 }

        assertEquals(ServerValidationFetch.Outcome.Superseded, outcome)
        assertEquals(1, counting.performCount)
    }


    @Test
    fun failureSurfacesWithoutRetrying() = async {
        val counting = CountingPerform(ExecutionFailure("Boom"))

        val outcome = fetch(counting)

        assertEquals(ServerValidationFetch.Outcome.Failed("Boom"), outcome)
        assertEquals(1, counting.performCount)
    }
}
