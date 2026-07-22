package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


/**
 * A SINK Worker that collects every incoming message and, at end-of-stream ([onComplete]), yields the collection
 * as the Job's named output component ([result], blank = "main") via [JobControl.yieldResult]. A LOGIC-BOUNDARY
 * worker: a [JobMessage] never crosses out of the Job, so each collected message materializes via
 * [JobMessage.boundaryValue] AT YIELD (payload when present, else the flat part as an ordered Map) — the buffer
 * keeps the raw messages, so the migration carryover below is untouched by the boundary rule. When exactly ONE
 * element arrived it yields that lone value instead of a singleton list — mirroring the hosts' single-positional
 * input convention (see [tech.kzen.auto.server.exec.job.EngineJobControl]), so a per-element `RunWorker` round trip
 * is scalar-in / scalar-out. Zero elements → an empty list. It is the Job-side analogue of Flow's `FlowOutput`
 * vertex, and what lets a `RunStep` / Flow `Run` vertex / Job `RunWorker` consume a Job's result.
 *
 * The default `main` component matters: the hosts' harvest (`RunWorker.onElement`, `RunStep.run`) reads
 * `mainComponentValue()`, so a single sink with default config satisfies it with zero configuration (Flow filters
 * a blank result name instead; Job diverges deliberately for the palette-insert-and-it-works path). A Job may
 * carry several ResultSinks, each yielding its own named component.
 *
 * LIVE-EDIT MIGRATION: the accumulated [collected] buffer is carried across a live edit ([SortWorker]'s-buffer
 * precedent) — REQUIRED for correctness, since an unchanged upstream resumes rather than replays, so a sink that
 * restarted empty would drop every pre-edit element. Unlike Sort it does NOT clear on yield: after a migrate the
 * rebuilt [JobRun] has a FRESH (empty) [tech.kzen.auto.server.exec.job.JobResultCollector], so the relaunched sink
 * (a Job relaunches completed Workers — logic-spec §5) re-drains the now-instantly-closed channel and re-yields at
 * [onComplete] — which works for free precisely because yield is an idempotent overwrite and the accumulation
 * survived. Clearing (the Sort reflex — where a re-emit downstream would be corruption) would silently lose the
 * result across a post-completion edit; yield has no such hazard. Capture is unconditional of [result] (the
 * buffered INPUT is valid under any component name); [SinkWorker]'s pre-receive checkpoint is the only park point,
 * so capture never cuts mid-[onComplete].
 */
@Reflect
class ResultSinkWorker(
    input: ChannelInput<Any?>,
    private val result: String,
    selfLocation: ObjectLocation
):
    SinkWorker(input, selfLocation)
{
    private var collected = ArrayList<JobMessage>()


    override suspend fun onElement(element: JobMessage, control: JobControl) {
        collected.add(element)
    }


    override suspend fun onComplete(control: JobControl) {
        val component = result.ifBlank { TupleComponentName.main.value }
        val value: Any? =
            if (collected.size == 1) {
                collected[0].boundaryValue()
            }
            else {
                collected.map { it.boundaryValue() }
            }
        control.yieldResult(component, value)
        // NB: collected is deliberately NOT cleared — a post-completion live edit relaunches this worker, which
        // adopts the carried accumulation and re-yields into the rebuilt run's fresh collector (yield is an
        // idempotent overwrite; clearing would lose the result).
    }


    override fun progress(snapshot: Any?): Map<String, Any?> =
        mapOf("collected" to collected.size.toLong())


    override fun captureMigrationState(): Any =
        CollectedState(collected)


    override fun loadMigrationState(captured: Any?) {
        val state = captured as? CollectedState
            ?: return
        collected = state.collected
    }


    private class CollectedState(val collected: ArrayList<JobMessage>)
}
