package tech.kzen.auto.server.objects.job.worker.test

import kotlinx.coroutines.awaitCancellation
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.job.worker.JobMessage
import tech.kzen.auto.server.objects.job.worker.SinkWorker
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong


/**
 * Test-only sink Worker for the deterministic channel-carryover migration test
 * ([tech.kzen.auto.server.objects.job.JobMigrationCarryoverTest]): counts every element it receives into the
 * static [received], and GATES its first instance so the migration's "buffer full + one send parked" state can
 * be reached deterministically.
 *
 * The gate is keyed to the instance number rather than an external release signal, which sidesteps any
 * release-timing race:
 *
 * - Instance #1 never drains (it [awaitCancellation]s in [onStart], before the drain loop), so the channel
 *   buffer fills and the upstream [GatedSourceWorker] parks mid-send. When the pause / edit / continue rebuilds
 *   the graph, teardown cancels this parked instance — it consumed nothing, so it counts nothing.
 * - Instance #2 (the rebuilt sink) skips the gate and drains normally: the migration carryover (the buffered
 *   payloads plus the one that was parked mid-send) is delivered first, then the position-resumed remainder.
 *
 * Because [received] is static and shared across instances, the final count is the total iff the migration
 * neither dropped nor double-delivered any in-flight payload.
 *
 * `@Reflect` with no KSP pass over the test source set: the graph instantiates it through the JVM reflective
 * mirror rather than a generated registration.
 */
@Reflect
class GatedCountingSinkWorker(
    input: ChannelInput<Any?>,

    // A no-op attribute the test edits (to a new value) to trigger a state migration without touching the
    // source's config (so the source resumes from its position rather than restarting).
    @Suppress("unused")
    private val note: String,

    selfLocation: ObjectLocation
):
    SinkWorker(input, selfLocation)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // Number of instances launched this run (first launch + one per migration). Instance #1 gates.
        val instances = AtomicInteger(0)

        // Total elements received across all instances. The migration is lossless iff this equals the source's
        // total at the end of the run.
        val received = AtomicLong(0)

        fun reset() {
            instances.set(0)
            received.set(0)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onStart(control: JobControl) {
        if (instances.incrementAndGet() == 1) {
            // First instance never drains: hold the channel buffer full so the source parks mid-send, building
            // the buffered + parked-mid-send carryover the migration must preserve. Suspends here until the
            // pause / edit / continue tears the graph down and cancels this coroutine; the rebuilt instance
            // (#2) skips this branch and drains normally.
            awaitCancellation()
        }
    }


    override suspend fun onElement(element: JobMessage, control: JobControl) {
        received.incrementAndGet()
    }
}
