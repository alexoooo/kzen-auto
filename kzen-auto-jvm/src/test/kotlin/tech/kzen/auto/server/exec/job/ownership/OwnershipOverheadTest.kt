package tech.kzen.auto.server.exec.job.ownership

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.server.objects.job.channel.JobChannel
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.lib.common.exec.logic.run.model.LogicRunId
import kotlin.test.assertEquals
import kotlin.test.assertTrue


/**
 * The E9 acceptance's throughput check: the lifecycle bookkeeping must not tax the ordinary unowned scalar
 * path. Streams lifted scalars through a [JobChannel] with and without a bound ledger and compares; the
 * numbers are printed for the session record, the assertion only guards against a gross regression.
 */
class OwnershipOverheadTest {
    private val elements = 300_000
    private val batchSize = 256
    private val warmupRounds = 2
    private val measuredRounds = 5

    // A gross regression is an order of magnitude; the two paths take single-digit milliseconds each, where a
    // shared machine's noise alone moves the ratio past 2 (seen at 2.12 with a Maven build alongside), so the
    // bound is loose and the printed numbers are the record.
    private val maximumSlowdown = 4.0


    @Test
    fun unownedScalarsCostNoMeasurableLifecycleOverhead() {
        repeat(warmupRounds) {
            stream(bound = false)
            stream(bound = true)
        }
        // Best of several rounds each: the guard is against lifecycle cost, not scheduler noise
        val unbound = (1..measuredRounds).minOf { stream(bound = false) }
        val bound = (1..measuredRounds).minOf { stream(bound = true) }
        println("E9 overhead: unowned scalars through a channel — unbound ${unbound / 1_000_000} ms, " +
            "ledger-bound ${bound / 1_000_000} ms for $elements elements")
        val slowdown = bound.toDouble() / unbound.toDouble().coerceAtLeast(1.0)
        assertTrue(slowdown < maximumSlowdown, "ledger-bound path is ${"%.2f".format(slowdown)}x the unbound one")
    }


    private fun stream(bound: Boolean): Long {
        val channel = JobChannel(capacity = 8, batchSize = batchSize)
        if (bound) {
            channel.bindOwnership(RunOwnershipLedger(LogicRunId("overhead"), NativeIdentityRegistry()), LeaseHolder("channel"))
        }
        val values = (0 until batchSize).map { JobDataValues.lift(it) }
        val start = System.nanoTime()
        var received = 0
        runBlocking {
            val producer = channel.newProducer()
            launch {
                for (index in 0 until elements) {
                    producer.send(values[index % batchSize])
                    if ((index + 1) % batchSize == 0) {
                        producer.flush()
                    }
                }
                producer.flush()
                producer.close()
            }
            while (true) {
                val batch = channel.input.receiveBatch() ?: break
                received += batch.size
            }
        }
        assertEquals(elements, received)
        return System.nanoTime() - start
    }
}
