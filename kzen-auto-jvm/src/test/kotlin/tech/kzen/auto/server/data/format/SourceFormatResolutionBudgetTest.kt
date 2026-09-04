package tech.kzen.auto.server.data.format

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.lib.platform.ClassName
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue


class SourceFormatResolutionBudgetTest {
    @Test
    fun defaultPolicyPinsTheAggregateLimits() {
        val policy = SourceFormatResolutionPolicy.default

        assertEquals(4, policy.maximumConcurrentColdParts)
        assertEquals(256, policy.maximumColdParts)
        assertEquals(64L * 1024 * 1024, policy.maximumDecodedBytes)
        assertEquals(15_000L, policy.overallTimeoutMillis)
    }


    @Test
    fun productionContextPublishesTheDefaultBudgetFactory() {
        val context = KzenAutoContext.forTest()
        try {
            val service = context.graphEnvironment.resolve(
                ClassName(SourceFormatResolutionBudgetFactory::class.qualifiedName!!))
            assertSame(context.sourceFormatResolutionBudgetFactory, service)
        }
        finally {
            context.close()
        }
    }


    @Test
    fun coldAcquisitionsNeverExceedTheConcurrencyLimit() = runBlocking {
        val operationTimeoutMillis = 5_000L
        val taskCount = 12
        val budget = SourceFormatResolutionBudget(SourceFormatResolutionPolicy(
            maximumConcurrentColdParts = policyConcurrencyLimit,
            maximumColdParts = taskCount,
            maximumDecodedBytes = taskCount.toLong(),
            overallTimeoutMillis = operationTimeoutMillis))
        val acquired = AtomicInteger()
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val firstWaveAcquired = CompletableDeferred<Unit>()
        val releaseFirstWave = CompletableDeferred<Unit>()

        budget.withinDeadline {
            coroutineScope {
                val tasks = (0 until taskCount).map {
                    async(Dispatchers.Default) {
                        val permit = acquireColdPart()
                        val activeNow = active.incrementAndGet()
                        peak.updateAndGet { maxOf(it, activeNow) }
                        if (acquired.incrementAndGet() == policyConcurrencyLimit) {
                            firstWaveAcquired.complete(Unit)
                        }
                        try {
                            releaseFirstWave.await()
                            active.decrementAndGet()
                            permit.completeSuccess()
                        }
                        finally {
                            permit.close()
                        }
                    }
                }
                firstWaveAcquired.await()
                assertEquals(policyConcurrencyLimit, acquired.get())
                releaseFirstWave.complete(Unit)
                tasks.awaitAll()
            }
        }

        assertEquals(policyConcurrencyLimit, peak.get())
        assertEquals(taskCount, budget.snapshot().completedParts)
        assertEquals(0, budget.snapshot().activeColdParts)
    }


    @Test
    fun coldPartLimitReportsCompletedCountAndCorrectiveActions() = runBlocking {
        val maximumColdParts = 2
        val budget = SourceFormatResolutionBudget(SourceFormatResolutionPolicy(
            maximumConcurrentColdParts = 1,
            maximumColdParts = maximumColdParts,
            maximumDecodedBytes = 10,
            overallTimeoutMillis = 1_000))

        repeat(maximumColdParts) {
            budget.acquireColdPart().completeSuccess()
        }
        val failure = runCatching { budget.acquireColdPart() }.exceptionOrNull()

        assertActionableFailure(failure, "2 completed file(s)", "cold-part limit of 2")
        assertEquals(maximumColdParts, budget.snapshot().coldPartsStarted)
        assertEquals(0, budget.snapshot().activeColdParts)
    }


    @Test
    fun decodedByteLimitIsSharedAcrossColdParts() = runBlocking {
        val maximumDecodedBytes = 5L
        val budget = SourceFormatResolutionBudget(SourceFormatResolutionPolicy(
            maximumConcurrentColdParts = 2,
            maximumColdParts = 2,
            maximumDecodedBytes = maximumDecodedBytes,
            overallTimeoutMillis = 1_000))
        val completed = budget.acquireColdPart()
        budget.chargeDecodedBytes(3)
        completed.completeSuccess()

        val failure = runCatching { budget.chargeDecodedBytes(3) }.exceptionOrNull()

        assertActionableFailure(failure, "1 completed file(s)", "decoded-sample limit of 5 bytes")
        assertEquals(3, budget.snapshot().decodedBytes)
    }


    @Test
    fun deadlineCancelsWorkAndReleasedPermitsRemainCloseSafe() = runBlocking {
        val timeoutMillis = 50L
        val longDelayMillis = 5_000L
        val budget = SourceFormatResolutionBudget(SourceFormatResolutionPolicy(
            maximumConcurrentColdParts = 1,
            maximumColdParts = 1,
            maximumDecodedBytes = 1,
            overallTimeoutMillis = timeoutMillis))

        val failure = runCatching {
            budget.withinDeadline {
                val permit = acquireColdPart()
                try {
                    delay(longDelayMillis)
                }
                finally {
                    permit.close()
                    permit.close()
                }
            }
        }.exceptionOrNull()

        assertActionableFailure(failure, "0 completed file(s)", "wall-time limit of 50 ms")
        assertEquals(0, budget.snapshot().activeColdParts)
        assertEquals(1, budget.snapshot().peakActiveColdParts)
    }


    @Test
    fun exhaustedColdTotalFailsWhileEveryConcurrencyPermitIsHeld() = runBlocking {
        val acquisitionTimeoutMillis = 500L
        val budget = SourceFormatResolutionBudget(SourceFormatResolutionPolicy(
            maximumConcurrentColdParts = policyConcurrencyLimit,
            maximumColdParts = policyConcurrencyLimit,
            maximumDecodedBytes = policyConcurrencyLimit.toLong(),
            overallTimeoutMillis = 5_000))
        val held = (0 until policyConcurrencyLimit).map {
            budget.acquireColdPart()
        }

        try {
            val failure = withTimeout(acquisitionTimeoutMillis) {
                runCatching { budget.acquireColdPart() }.exceptionOrNull()
            }
            assertActionableFailure(
                failure,
                "0 completed file(s)",
                "cold-part limit of 4")
        }
        finally {
            held.forEach { it.close() }
        }
    }


    @Test
    fun cancelledConcurrencyWaitRollsBackItsColdReservation() = runBlocking {
        val waitForReservationMillis = 500L
        val budget = SourceFormatResolutionBudget(SourceFormatResolutionPolicy(
            maximumConcurrentColdParts = 1,
            maximumColdParts = 2,
            maximumDecodedBytes = 2,
            overallTimeoutMillis = 5_000))
        val held = budget.acquireColdPart()
        val waiting = launch(Dispatchers.Default) {
            budget.acquireColdPart().close()
        }

        withTimeout(waitForReservationMillis) {
            while (budget.snapshot().coldPartsStarted != 2) {
                delay(1)
            }
        }
        waiting.cancelAndJoin()

        assertEquals(1, budget.snapshot().coldPartsStarted)
        held.close()
        budget.acquireColdPart().close()
        assertEquals(2, budget.snapshot().coldPartsStarted)
    }


    private fun assertActionableFailure(failure: Throwable?, vararg messages: String) {
        val typed = assertIs<IllegalStateException>(failure)
        messages.forEach { assertTrue(typed.message!!.contains(it), typed.message) }
        assertTrue(typed.message!!.contains("Narrow the file filter"), typed.message)
        assertTrue(typed.message!!.contains("choose a concrete source-level format"), typed.message)
        assertTrue(typed.message!!.contains("raise the source-resolution policy"), typed.message)
    }


    companion object {
        private const val policyConcurrencyLimit = 4
    }
}
