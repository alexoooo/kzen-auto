package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


/**
 * A SOURCE Worker that streams a Job's bound run argument for the parameter it declares ([parameter]): a
 * `Collection<*>` argument is streamed element-by-element, any OTHER value (including an unbound null) is emitted
 * as a single element. It is the Job-side analogue of Flow's `FlowInput` vertex — what makes a Job invocable with
 * arguments from a Script `RunStep` / Flow `Run` vertex / Job `RunWorker`. The argument is read via
 * [JobControl.parameter], seeded from the run's typed inputs (see
 * [tech.kzen.auto.server.exec.job.EngineJobControl]). The output channel is UNTYPED (`Any?`, like
 * [FormulaSourceWorker] / [RunWorker]) — a concrete archetype opting into an `of:` on its output port types the
 * Job's signature with no code change; for a Collection argument that `of:` describes the ELEMENT type.
 *
 * `Collection<*>` (NOT `Iterable`) is the streaming trigger, exactly as the plan words it: an `IntRange` / sequence
 * argument arrives as a single element unless the caller `.toList()`s it — kept a simple, documented rule.
 *
 * LIVE-EDIT MIGRATION: the *values* need no carry — the rebuilt run re-receives identical [JobControl.parameter]
 * arguments (run-constant) and re-derives the same list — but the STREAM POSITION must carry, or a mid-stream
 * migrate re-emits already-delivered elements while the carried ResultSink accumulation keeps the old ones
 * (duplicates). So [nextIndex] is captured / restored (the claim-before-send cursor precedent of
 * [tech.kzen.auto.server.objects.job.worker.test.GatedSourceWorker] /
 * [CsvReaderWorker]'s file-position resume): the index is claimed BEFORE [Emitter.send], because a send parked
 * mid-flush holds its payload in the channel's in-flight buffer (carried by the migration's
 * [tech.kzen.auto.server.objects.job.channel.JobChannel.drainBuffered]) — so the resumed source must not re-send
 * it. The cursor is guarded on the [parameter] name: a renamed parameter is a different stream, so it restarts.
 * (The list is re-indexed on the rebuilt instance, which is why a Collection argument must be stably ordered — a
 * `List` from a caller always is.)
 */
@Reflect
class ParameterSourceWorker(
    output: ChannelOutput<Any?>,
    private val parameter: String,
    selfLocation: ObjectLocation
):
    SourceWorker(output, selfLocation)
{
    // Next element index to emit; claimed BEFORE send (a send parked mid-flush holds its payload in the channel's
    // in-flight buffer, carried by the migration's drainBuffered — the resumed source must not re-send it).
    // @Volatile: written on the worker coroutine, read at the capture barrier while the worker is parked.
    @Volatile
    private var nextIndex = 0


    override suspend fun produce(emit: Emitter, control: JobControl) {
        val argument = control.parameter(parameter)
        val elements: List<Any?> =
            when (argument) {
                is Collection<*> -> argument.toList()
                else -> listOf(argument)
            }

        while (nextIndex < elements.size) {
            // The SourceWorker cadence checkpoints + flushes + publishes per batch, so this loop just emits.
            val element = elements[nextIndex]
            nextIndex += 1
            emit.send(JobMessage.ofPayload(element))
        }
    }


    override fun progress(snapshot: Any?): Map<String, Any?> =
        mapOf("emitted" to nextIndex.toLong())


    override fun captureMigrationState(): Any =
        ParameterCursor(parameter, nextIndex)


    override fun loadMigrationState(captured: Any?) {
        val cursor = captured as? ParameterCursor
            ?: return
        // Config-changed guard: a renamed parameter is a different stream, so restart rather than resume.
        if (cursor.parameter == parameter) {
            nextIndex = cursor.nextIndex
        }
    }


    private class ParameterCursor(val parameter: String, val nextIndex: Int)
}
