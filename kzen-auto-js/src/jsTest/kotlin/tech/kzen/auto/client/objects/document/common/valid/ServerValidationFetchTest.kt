package tech.kzen.auto.client.objects.document.common.valid

import tech.kzen.auto.client.util.async
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.NullExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.util.digest.Digest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class ServerValidationFetchTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val currentNotationDigest = Digest.ofUtf8("v2")
    private val staleNotationDigest = Digest.ofUtf8("v1")

    // Mirrors ServerValidationFetch's own cap; a change there must be reflected here.
    private val staleRetryLimit = 10


    private fun value(marker: String): MapExecutionValue {
        return MapExecutionValue(mapOf(marker to NullExecutionValue))
    }


    private fun response(marker: String, echoedDigest: Digest): ExecutionResult {
        return ExecutionSuccess(value(marker), TextExecutionValue(echoedDigest.asString()))
    }


    // Pops the scripted responses in order, repeating the last one once exhausted, and counts the calls.
    private class ScriptedPerform(private val responses: List<ExecutionResult>) {
        var performCount = 0
            private set

        suspend fun perform(): ExecutionResult {
            val response = responses[minOf(performCount, responses.size - 1)]
            performCount++
            return response
        }
    }


    private suspend fun fetch(
        scripted: ScriptedPerform,
        currentDigest: () -> Digest? = { currentNotationDigest }
    ): ServerValidationFetch.Outcome<MapExecutionValue> {
        return ServerValidationFetch.fetchCurrent(
            currentDigest = currentDigest,
            perform = { scripted.perform() },
            parse = { it })
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun matchingEchoAppliesImmediately() = async {
        val scripted = ScriptedPerform(listOf(response("current", currentNotationDigest)))

        val outcome = fetch(scripted)

        assertEquals(ServerValidationFetch.Outcome.Current(value("current")), outcome)
        assertEquals(1, scripted.performCount)
    }


    @Test
    fun staleEchoRetriesUntilCurrent() = async {
        val scripted = ScriptedPerform(listOf(
            response("stale", staleNotationDigest),
            response("current", currentNotationDigest)))

        val outcome = fetch(scripted)

        assertEquals(ServerValidationFetch.Outcome.Current(value("current")), outcome)
        assertEquals(2, scripted.performCount)
    }


    @Test
    fun neverCurrentFailsRatherThanApplyingStale() = async {
        val scripted = ScriptedPerform(listOf(response("stale", staleNotationDigest)))

        val outcome = fetch(scripted)

        assertTrue(outcome is ServerValidationFetch.Outcome.Failed, "Outcome: $outcome")
        assertEquals(staleRetryLimit, scripted.performCount)
    }


    @Test
    fun supersededAbortsWithoutRefetching() = async {
        val scripted = ScriptedPerform(listOf(response("stale", staleNotationDigest)))
        var superseded = false

        val outcome = fetch(scripted) {
            if (superseded) {
                null
            }
            else {
                superseded = true
                currentNotationDigest
            }
        }

        assertEquals(ServerValidationFetch.Outcome.Superseded, outcome)
        assertEquals(1, scripted.performCount)
    }


    @Test
    fun missingEchoAppliesUnconditionally() = async {
        val scripted = ScriptedPerform(listOf(
            ExecutionSuccess(value("unechoed"), NullExecutionValue)))

        val outcome = fetch(scripted)

        assertEquals(ServerValidationFetch.Outcome.Current(value("unechoed")), outcome)
        assertEquals(1, scripted.performCount)
    }


    @Test
    fun failureSurfacesWithoutRetrying() = async {
        val scripted = ScriptedPerform(listOf(ExecutionFailure("Boom")))

        val outcome = fetch(scripted)

        assertEquals(ServerValidationFetch.Outcome.Failed("Boom"), outcome)
        assertEquals(1, scripted.performCount)
    }
}
