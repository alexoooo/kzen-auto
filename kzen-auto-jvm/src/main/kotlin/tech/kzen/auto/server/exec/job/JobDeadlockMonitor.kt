package tech.kzen.auto.server.exec.job

import org.slf4j.LoggerFactory
import tech.kzen.auto.server.objects.job.channel.JobChannel
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger


/**
 * Channel-aware, Job-scoped deadlock detection — the successor to the retired engine watchdog (whose `>= 2-leaf`
 * topology rule sat in the use-case-agnostic core and MISSED a lone sink on an orphan channel). Deadlock is a
 * dataflow concept, so it belongs to the flavour that owns the channels: this polls a run's stream
 * [JobChannel]s on a dedicated daemon timer and fails the run when the pipeline can provably make no progress.
 *
 * The signal is precise because it reads channel state, not raw quiescence: a Job is deadlocked iff EVERY
 * non-terminal Worker is suspended on a channel op ([sum of blockedCount][JobChannel.blockedCount] `>=`
 * [activeWorkers]). This exactly separates the three look-alike quiescent states the old inFlight-only heuristic
 * confused:
 * - **Paused / stepping** — Workers park at a [JobControl.checkpoint][tech.kzen.auto.common.paradigm.job.control.JobControl.checkpoint]
 *   (engine node `Suspended`), NOT inside a channel op, so `blocked < active` and no verdict fires (a sink
 *   checkpoints BEFORE it receives — see SinkWorker).
 * - **Externally-gated waits** — a Worker parked on a non-channel latch (a test gate, a `host`ed child) is not
 *   blocked on a channel, so `blocked < active`.
 * - **Genuine deadlock** — a lone sink on an orphan channel, or a cycle of Workers each waiting on the other:
 *   every Worker suspended on a channel that will never deliver → `blocked == active` → failed.
 *
 * Suppressed entirely while the run serves an external duplex channel: a Worker idle on an open serve port
 * awaits a UI request, indistinguishable from a channel block under this heuristic (matches the retired
 * `JobExecution`; a precise per-Worker fix is deferred). A transient wavefront where a handoff briefly leaves
 * every Worker suspended is absorbed by requiring the condition to hold across [graceThreshold] consecutive
 * polls, so only a SUSTAINED stall reaches a verdict.
 *
 * Runs OFF the engine dispatcher (its own daemon thread): a polling coroutine on the engine's CountingDispatcher
 * would keep `inFlight` bouncing and break the controller's [awaitQuiescent][tech.kzen.lib.server.exec.engine.RunEngine.awaitQuiescent].
 */
class JobDeadlockMonitor(
    private val streamChannels: Collection<JobChannel>,
    private val activeWorkers: AtomicInteger,
    private val externallyServing: Boolean,
    private val onDeadlock: () -> Unit
): AutoCloseable {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(JobDeadlockMonitor::class.java)

        // Re-check the pipeline this often while it is running.
        private const val pollIntervalMillis = 50L

        // Consecutive all-blocked polls before declaring deadlock — absorbs a transient wavefront where a channel
        // handoff briefly leaves every Worker suspended, so only quiescence SUSTAINED across ~200 ms reaches a
        // verdict. The Job-scoped analogue of the old JobExecution deadlock grace, expressed as poll ticks.
        private const val graceThreshold = 4
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "kzen-job-deadlock-monitor").apply { isDaemon = true }
    }

    // Touched only on the single scheduler thread.
    private var consecutiveStalls = 0

    @Volatile
    private var fired = false


    //-----------------------------------------------------------------------------------------------------------------
    fun start() {
        if (externallyServing) {
            // Deadlock detection is suspended for the whole run while any external channel is open.
            return
        }
        executor.scheduleWithFixedDelay(
            ::poll, pollIntervalMillis, pollIntervalMillis, TimeUnit.MILLISECONDS)
    }


    override fun close() {
        executor.shutdownNow()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun poll() {
        if (fired) {
            return
        }

        val active = activeWorkers.get()
        if (active <= 0) {
            // No non-terminal Worker to be stuck: the run is completing (or already done).
            consecutiveStalls = 0
            return
        }

        val blocked = streamChannels.sumOf { it.blockedCount() }
        if (blocked < active) {
            // At least one Worker is running / computing / parked at a checkpoint / gated on a non-channel wait —
            // the pipeline is not wholly channel-blocked, so it can still make progress.
            consecutiveStalls = 0
            return
        }

        // Every non-terminal Worker is suspended on a channel op: no Worker can feed another, so the run is
        // stalled. Confirm across the grace window before the verdict.
        consecutiveStalls += 1
        if (consecutiveStalls >= graceThreshold) {
            fired = true
            logger.warn("Job deadlock: {} worker(s) all blocked on channels with no progress", active)
            onDeadlock()
        }
    }
}
