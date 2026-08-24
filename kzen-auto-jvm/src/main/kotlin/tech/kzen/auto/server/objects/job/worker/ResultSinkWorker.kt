package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.common.objects.document.logic.ResultSignatureDefiner
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.common.paradigm.logic.LogicConventions
import tech.kzen.auto.server.objects.logic.TypeAssignability
import tech.kzen.auto.server.service.compile.CachedKotlinCompiler
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.platform.ClassNames


/**
 * A SINK Worker that keeps the first, last, or every element of the stream ([keep]: [first] / [last] / [all])
 * and, at end-of-stream
 * ([onComplete]), yields it as the Job's named output component ([result], blank = "main") via
 * [JobControl.yieldResult] — the Job-side analogue of Script's Result step, typed by the Job document's declared
 * `results` signature (Script parity: yielding into an undeclared component — or a stream whose inferred
 * boundary type is not assignable to the declared type — is a validation error, surfaced by [payloadFlow] on
 * the card before running; the undeclared case fails the run too). An empty stream is a run failure unless the declared
 * type is nullable (then the result is null) — so a Job whose stream unexpectedly dried up fails loudly instead
 * of returning a silent empty. A LOGIC-BOUNDARY worker: a [JobMessage] never crosses out of the Job, so the kept
 * message materializes via [JobMessage.boundaryValue] AT YIELD (payload when present, else the flat part as an
 * ordered Map) — the field keeps the raw message, so the migration carryover below is untouched by the boundary
 * rule. What lets a `RunStep` / Flow `Run` vertex / Job `RunWorker` consume a Job's result; the default `main`
 * component matters: the hosts' harvest (`RunWorker.onElement`, `RunStep.run`) reads `mainComponentValue()`, so
 * a single sink with default config satisfies it with zero configuration. A Job may carry several ResultSinks,
 * each yielding its own declared named component. [progress] also pushes the kept value's display text so the
 * card's ResultWorkerDisplay shows it in a value box (live for [last], settled on the forced final publish).
 *
 * LIVE-EDIT MIGRATION: the kept element (and the seen-count) is carried across a live edit ([SortWorker]'s-buffer
 * precedent) — REQUIRED for correctness, since an unchanged upstream resumes rather than replays, so a sink that
 * restarted empty would drop every pre-edit element (under [first], the first element is likely long gone). It
 * does NOT clear on yield: after a migrate the rebuilt [JobRun] has a FRESH (empty)
 * [tech.kzen.auto.server.exec.job.JobResultCollector], so the relaunched sink (a Job relaunches completed
 * Workers — logic-spec §5) re-drains the now-instantly-closed channel and re-yields at [onComplete] — which
 * works for free precisely because yield is an idempotent overwrite and the kept element survived. Capture is
 * same-mode only: editing [keep] restarts rather than interpreting old state under new semantics. [all] keeps
 * raw boundary values in encounter order and is intentionally unbounded in memory; migration defensively copies
 * its list. Capture is unconditional of [result]; [SinkWorker]'s
 * pre-receive checkpoint is the only park point, so capture never cuts mid-[onComplete].
 */
@Reflect
class ResultSinkWorker(
    input: ChannelInput<Any?>,
    private val result: String,
    private val keep: String,
    private val selfLocation: ObjectLocation,
    @Service private val cachedKotlinCompiler: CachedKotlinCompiler
):
    SinkWorker(input, selfLocation)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        const val first = "first"
        const val last = "last"
        const val all = "all"

        // Upper bound on the pushed value's display text — every progress emit is retained in the engine's
        // unbounded history, so a Worker's published payload must stay O(bounded) (the WorkerBase teaser rule).
        // A larger result is elided with "…"; the box shows the head, which is what a card preview needs.
        private const val maxDisplayValueLength = 10_000

        private fun noResultDeclared(component: String): String =
            "No result type declared in the Job signature for '$component'"

        private fun displayText(value: Any?): String {
            val text = value.toString()
            return if (text.length > maxDisplayValueLength) {
                text.substring(0, maxDisplayValueLength) + "…"
            }
            else {
                text
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var kept: JobMessage? = null
    private val keptAll = mutableListOf<Any?>()
    private var hasAny = false
    private var collected = 0L


    private fun componentName(): String =
        result.ifBlank { TupleComponentName.main.value }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onElement(element: JobMessage, control: JobControl) {
        collected++
        if (keep == all) {
            keptAll.add(element.boundaryValue())
            kept = element
            hasAny = true
            return
        }
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

        if (keep == all) {
            control.yieldResult(component, keptAll.toList())
            return
        }

        if (!hasAny) {
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
    // Static validation (identity lane — a sink's card displays its input), the strict Script-parity checks,
    // all surfaced on the card before running: the `keep` value; this sink's component is declared in the Job
    // document's `results` signature map; and the lane's inferred boundary type is ASSIGNABLE to the declared
    // type ([TypeAssignability]'s probe compile — full Kotlin subtyping/nullability, ResultStep's forced-type
    // strictness). A statically unknown lane ([WorkerLane.boundaryType] null) skips the assignability check —
    // its mismatch surfaces at run time as before.
    override fun payloadFlow(input: WorkerLane, context: WorkerLaneContext): WorkerLaneAttempt {
        if (keep != first && keep != last && keep != all) {
            return WorkerLaneAttempt(input, "Invalid result sink 'keep': $keep")
        }

        val mainLocation = ObjectLocation(selfLocation.documentPath, NotationConventions.mainObjectPath)
        val declaredResults = ResultSignatureDefiner.parse(
            context.graphStructure.graphNotation.firstAttribute(
                mainLocation, LogicConventions.resultsAttributePath))

        val component = componentName()
        val declaredType = declaredResults.find(TupleComponentName(component))?.metadata
            ?: return WorkerLaneAttempt(input, noResultDeclared(component))

        val elementType = input.boundaryType()
        val boundaryType =
            if (keep == all && elementType != null) {
                TypeMetadata(ClassNames.kotlinList, listOf(elementType), false)
            }
            else {
                elementType
            }
        if (boundaryType != null &&
                !TypeAssignability.isAssignable(
                    boundaryType, declaredType, cachedKotlinCompiler, context.classLoader)) {
            return WorkerLaneAttempt(
                input,
                "Result '$component' declares ${declaredType.toSimple()} " +
                    "but the stream carries ${boundaryType.toSimple()}")
        }

        return WorkerLaneAttempt(input, null)
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The kept count plus the kept value's display text (for ResultWorkerDisplay's value box). The value is
    // published as a single-element List so the generic default-card status line skips it (only the per-type
    // display renders it — see JobConventions.progressResultValueKey); the forced final publish (WorkerBase.run,
    // after onComplete) carries the settled value, and for keep=last the throttled pushes show the running
    // latest live. An empty stream (kept == null) publishes no value key — the box then renders nothing.
    override fun progress(snapshot: Any?): Map<String, Any?> {
        val progress = LinkedHashMap<String, Any?>()
        progress["collected"] = collected
        kept?.boundaryValue()?.let { value ->
            progress[JobConventions.progressResultValueKey] = listOf(displayText(value))
        }
        return progress
    }


    override fun captureMigrationState(): Any =
        KeptState(keep, kept, keptAll.toList(), hasAny, collected)


    override fun loadMigrationState(captured: Any?) {
        val state = captured as? KeptState
            ?: return
        if (state.keep != keep) {
            return
        }
        kept = state.kept
        keptAll.addAll(state.keptAll)
        hasAny = state.hasAny
        collected = state.collected
    }


    private class KeptState(
        val keep: String,
        val kept: JobMessage?,
        val keptAll: List<Any?>,
        val hasAny: Boolean,
        val collected: Long)
}
