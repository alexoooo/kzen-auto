package tech.kzen.auto.server.exec.job

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


/**
 * Direct coverage of the two clocks in [JobDeadlockMonitor] under an EXTERNALLY SERVING run, the shape whose
 * live-host acceptance (a governed accumulator retaining owned symbol-days behind a Preview) went quiet: the
 * monitor used to skip its whole schedule while any external channel was open, which also silenced the E9
 * retained-lease stall warning. Now only the failing verdict is suspended for a serving run; the non-failing
 * no-progress warning keeps reading the progress mark, and clears once the mark moves.
 */
class JobDeadlockMonitorTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun servingRunStillWarnsOnNoProgressAndNeverFailsAsDeadlock() {
        val stalled = CountDownLatch(1)
        val recovered = CountDownLatch(1)
        val deadlocks = AtomicInteger()
        val mark = java.util.concurrent.atomic.AtomicLong(0)

        // No stream channels at all, so the blocked heuristic would say `0 < 1` and never fail even if it ran;
        // the assertion that matters is that the verdict path is skipped while the progress clock still ticks.
        JobDeadlockMonitor(
            streamChannels = emptyList(),
            activeWorkers = AtomicInteger(1),
            externallyServing = true,
            onDeadlock = { deadlocks.incrementAndGet() },
            progressMark = { mark.get() },
            onStall = { isStalled ->
                if (isStalled) {
                    stalled.countDown()
                }
                else {
                    recovered.countDown()
                }
            }
        ).use { monitor ->
            monitor.start()

            assertTrue(
                stalled.await(JobDeadlockMonitor.stallIntervalMillis * 5, TimeUnit.MILLISECONDS),
                "Stall warning must fire for a serving run once the mark has not moved for the stall interval")
            assertEquals(1, recovered.count, "No recovery before the mark moves")

            mark.incrementAndGet()
            assertTrue(
                recovered.await(JobDeadlockMonitor.stallIntervalMillis * 5, TimeUnit.MILLISECONDS),
                "Recovery must be reported once the progress mark advances")
        }

        assertEquals(0, deadlocks.get(), "A serving run never receives the failing verdict")
    }


    @Test
    fun idleRunWithNoLiveWorkersNeverWarns() {
        val stalled = CountDownLatch(1)
        JobDeadlockMonitor(
            streamChannels = emptyList(),
            activeWorkers = AtomicInteger(0),
            externallyServing = true,
            onDeadlock = {},
            progressMark = { 0L },
            onStall = { if (it) stalled.countDown() }
        ).use { monitor ->
            monitor.start()
            assertFalse(
                stalled.await(JobDeadlockMonitor.stallIntervalMillis * 2, TimeUnit.MILLISECONDS),
                "No live Worker, nothing to be stalled")
        }
    }
}
