package tech.kzen.auto.server.objects.job.worker.test

import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.job.worker.Emitter
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.objects.job.worker.SourceWorker
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import java.util.concurrent.atomic.AtomicInteger


/**
 * Test-only source Worker for the deterministic channel-carryover migration test
 * ([tech.kzen.auto.server.objects.job.JobMigrationCarryoverTest]): emits `row0`..`row(total-1)` as plain
 * Strings, one per send, and RESUMES rather than restarts across a state migration (mirrors
 * a configured source's cursor resume).
 *
 * Two properties make the migration's channel carryover testable end-to-end and deterministically:
 *
 * - It claims each index BEFORE sending ([nextIndex] += 1 then [Emitter.send]). A send that parks on a full
 *   buffer holds its payload in the channel's `inFlight` (captured by `JobChannel.drainBuffered`), so the
 *   resumed source must NOT re-send that index — pre-incrementing makes [nextIndex] already point PAST the
 *   parked row, so carryover + resume together deliver every row exactly once.
 * - It counts sends INITIATED in the static [sendsStarted]. Paired with a never-draining sink
 *   ([GatedCountingSinkWorker]) and a channel of capacity N, the source can initiate exactly N + 1 sends
 *   (N buffered, the (N+1)-th parked mid-send) and no more — a STABLE, observable state the test waits for to
 *   pause precisely on "buffer full + one send parked", with no wall-clock race.
 *
 * `@Reflect` with no KSP pass over the test source set: the graph instantiates it through the JVM reflective
 * mirror rather than a generated registration.
 */
@Reflect
class GatedSourceWorker(
    output: ChannelOutput<Any?>,
    private val total: Int,
    selfLocation: ObjectLocation
):
    SourceWorker(output, selfLocation)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // Number of sends INITIATED across all live instances (incremented just before each send). With a
        // channel of capacity N and a non-draining sink this settles at exactly N + 1, letting the test detect
        // the "buffer full + one send parked" state deterministically. Reset by the test before each run.
        val sendsStarted = AtomicInteger(0)

        fun reset() {
            sendsStarted.set(0)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Index of the next row to emit; survives a migration so the rebuilt source RESUMES from here rather than
    // restarting from 0. @Volatile: written on the worker coroutine, read by the run driver in
    // captureMigrationState while the worker is parked.
    @Volatile
    private var nextIndex = 0


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun produce(emit: Emitter, control: JobControl) {
        while (nextIndex < total) {
            val row = "row$nextIndex"
            // Claim the row BEFORE sending: a send parked on a full buffer holds its payload in the channel's
            // inFlight (drained into the migration carryover), so on resume this index must not re-send it.
            nextIndex += 1
            sendsStarted.incrementAndGet()
            emit.send(JobDataValues.lift(row))
            control.checkpoint()
        }
    }


    override fun captureMigrationState(): Any =
        nextIndex


    override fun loadMigrationState(captured: Any?) {
        nextIndex = captured as Int
    }
}
