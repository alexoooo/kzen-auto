package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.objects.document.logic.ResultSignatureDefiner
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.common.paradigm.logic.LogicConventions
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.service.notation.NotationConventions


/**
 * A SINK Worker that keeps a single element of the stream ([keep]: [first] / [last]) and, at end-of-stream
 * ([onComplete]), yields it as the Job's named output component ([result], blank = "main") via
 * [JobControl.yieldResult] — the Job-side analogue of Script's Result step, typed by the Job document's declared
 * `results` signature (Script parity: yielding into an undeclared component is a validation error, surfaced by
 * [payloadFlow] on the card and again as a run failure). An empty stream is a run failure unless the declared
 * type is nullable (then the result is null) — so a Job whose stream unexpectedly dried up fails loudly instead
 * of returning a silent empty. A LOGIC-BOUNDARY worker: a [JobMessage] never crosses out of the Job, so the kept
 * message materializes via [JobMessage.boundaryValue] AT YIELD (payload when present, else the flat part as an
 * ordered Map) — the field keeps the raw message, so the migration carryover below is untouched by the boundary
 * rule. What lets a `RunStep` / Flow `Run` vertex / Job `RunWorker` consume a Job's result; the default `main`
 * component matters: the hosts' harvest (`RunWorker.onElement`, `RunStep.run`) reads `mainComponentValue()`, so
 * a single sink with default config satisfies it with zero configuration. A Job may carry several ResultSinks,
 * each yielding its own declared named component.
 *
 * LIVE-EDIT MIGRATION: the kept element (and the seen-count) is carried across a live edit ([SortWorker]'s-buffer
 * precedent) — REQUIRED for correctness, since an unchanged upstream resumes rather than replays, so a sink that
 * restarted empty would drop every pre-edit element (under [first], the first element is likely long gone). It
 * does NOT clear on yield: after a migrate the rebuilt [JobRun] has a FRESH (empty)
 * [tech.kzen.auto.server.exec.job.JobResultCollector], so the relaunched sink (a Job relaunches completed
 * Workers — logic-spec §5) re-drains the now-instantly-closed channel and re-yields at [onComplete] — which
 * works for free precisely because yield is an idempotent overwrite and the kept element survived. Capture is
 * unconditional of [result] / [keep] (the kept INPUT is valid under any component name); [SinkWorker]'s
 * pre-receive checkpoint is the only park point, so capture never cuts mid-[onComplete].
 */
@Reflect
class ResultSinkWorker(
    input: ChannelInput<Any?>,
    private val result: String,
    private val keep: String,
    private val selfLocation: ObjectLocation
):
    SinkWorker(input, selfLocation)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        const val first = "first"
        const val last = "last"

        private fun noResultDeclared(component: String): String =
            "No result type declared in the Job signature for '$component'"
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var kept: JobMessage? = null
    private var hasAny = false
    private var collected = 0L


    private fun componentName(): String =
        result.ifBlank { TupleComponentName.main.value }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onElement(element: JobMessage, control: JobControl) {
        collected++
        if (keep == first && hasAny) {
            return
        }
        kept = element
        hasAny = true
    }


    override suspend fun onComplete(control: JobControl) {
        val component = componentName()
        val declaredType = control.results().find(TupleComponentName(component))?.metadata
            ?: error(noResultDeclared(component))

        if (! hasAny) {
            check(declaredType.nullable) {
                "No element arrived for Result '$component' — declare the result nullable to allow an empty stream"
            }
            control.yieldResult(component, null)
            return
        }

        control.yieldResult(component, kept?.boundaryValue())
        // NB: kept is deliberately NOT cleared — a post-completion live edit relaunches this worker, which
        // adopts the carried element and re-yields into the rebuilt run's fresh collector (yield is an
        // idempotent overwrite; clearing would lose the result).
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Static validation (identity lane — a sink's card displays its input): the strict Script-parity check
    // that this sink's component is declared in the Job document's `results` signature map, plus the `keep`
    // value check — both surfaced on the card before running.
    override fun payloadFlow(input: WorkerLane, context: WorkerLaneContext): WorkerLaneAttempt {
        if (keep != first && keep != last) {
            return WorkerLaneAttempt(input, "Invalid result sink 'keep': $keep")
        }

        val mainLocation = ObjectLocation(selfLocation.documentPath, NotationConventions.mainObjectPath)
        val declaredResults = ResultSignatureDefiner.parse(
            context.graphStructure.graphNotation.firstAttribute(
                mainLocation, LogicConventions.resultsAttributePath))

        val component = componentName()
        if (declaredResults.find(TupleComponentName(component)) == null) {
            return WorkerLaneAttempt(input, noResultDeclared(component))
        }

        return WorkerLaneAttempt(input, null)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun progress(snapshot: Any?): Map<String, Any?> =
        mapOf("collected" to collected)


    override fun captureMigrationState(): Any =
        KeptState(kept, hasAny, collected)


    override fun loadMigrationState(captured: Any?) {
        val state = captured as? KeptState
            ?: return
        kept = state.kept
        hasAny = state.hasAny
        collected = state.collected
    }


    private class KeptState(val kept: JobMessage?, val hasAny: Boolean, val collected: Long)
}
