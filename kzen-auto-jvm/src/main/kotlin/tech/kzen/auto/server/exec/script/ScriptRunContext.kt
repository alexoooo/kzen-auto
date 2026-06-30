package tech.kzen.auto.server.exec.script

import tech.kzen.auto.common.objects.document.script.model.StepTrace
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.NullExecutionValue
import tech.kzen.lib.common.exec.engine.Address
import tech.kzen.lib.common.exec.engine.Execution
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.service.store.normal.ObjectStableId


/**
 * Per-run state threaded through a Script's steps: the engine [execution] (checkpoint / emit / host / pause),
 * the latest value each step produced (the in-scope value graph a downstream expression references), and the
 * captured Script result (last [setResult] wins — the [ResultStep][tech.kzen.auto.server.exec.script.step.ResultStep]
 * semantics). It is owned by the single [ScriptLogic] coroutine, never shared, so it needs no synchronization.
 *
 * Trace emission mirrors the former MultiStep so the existing client display is unchanged: a step's live value
 * is a [StepTrace] (state + display) addressed by its stable id, and the "next step to run" highlight is a
 * reserved-address emit ([nextStepAddressMarker]) the controller's trace bridge routes to
 * [tech.kzen.auto.common.objects.document.script.ScriptConventions.nextStepTracePath].
 *
 * LIVE-EDIT MIGRATION (logic-spec §5): the context also carries the run's completed work across a live edit. As
 * each step finishes its outcome is recorded in [completedOutcomes] (the capture source — see [captureState]); on
 * the rebuilt run [restore] seeds [restoredOutcomes] from the predecessor's capture so the [SequenceStep] spine
 * can replay-short-circuit completed steps (see [isReplayCompleted] / [adoptCompleted]) and a re-running loop can
 * drop its body's stale outcomes ([dropReplay]). See [ScriptMigrationState] for the carried shape and its bounds.
 */
class ScriptRunContext(
    val execution: Execution
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // Reserved emit address marking the "next step to run" highlight, distinct from a step's own value
        // address (which is the step's stable id). The trace bridge recognizes it and routes to the fixed
        // next-step trace path; a stable id can never collide (it is an ObjectLocation string).
        const val nextStepAddressMarker = "\$next-step"
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The live value graph downstream expressions reference (step outcomes + non-step bindings).
    private val stepValues = HashMap<ObjectStableId, Any?>()

    // The outcome each step that COMPLETED produced, in completion order — the live-edit capture source. Distinct
    // from stepValues: it excludes the non-step bindings (a parameter / loop item), which the rebuilt run
    // re-derives rather than carries.
    private val completedOutcomes = LinkedHashMap<ObjectStableId, Any?>()

    // The predecessor run's completed outcomes, seeded by restore() across a live edit; consulted by the spine to
    // replay-short-circuit and pruned by a re-running loop (dropReplay). Empty on a fresh (non-migration) run.
    private val restoredOutcomes = HashMap<ObjectStableId, Any?>()

    private var resultValue: TupleValue? = null


    //-----------------------------------------------------------------------------------------------------------------
    /** Resolve the value a predecessor step (or loop-item / parameter binding) produced. */
    fun referencedValue(stableId: ObjectStableId): Any? {
        check(stepValues.containsKey(stableId)) { "No value produced for: $stableId" }
        return stepValues[stableId]
    }


    /**
     * Record a value for downstream reference WITHOUT emitting a step-trace entry — for the non-step bindings
     * (a parameter, a loop item) that are resolved by reference but never shown as steps in the spine. Not part
     * of [completedOutcomes]: bindings are re-derived on the rebuilt run, not carried across a live edit.
     */
    fun record(stableId: ObjectStableId, value: Any?) {
        stepValues[stableId] = value
    }


    /** Publish the "next step to run" highlight (or clear it with null when a sequence is exhausted). */
    fun publishNextStep(stableId: ObjectStableId?) {
        val value = if (stableId == null) NullExecutionValue else ExecutionValue.of(stableId.value)
        execution.emit(Address.of(nextStepAddressMarker), value)
    }


    /** Mark a step as currently running (no value yet). */
    fun markRunning(stableId: ObjectStableId) {
        emitStepTrace(stableId, StepTrace.State.Running, NullExecutionValue)
    }


    /**
     * Mark a step as failed (its error rendered for the client). Used by the pause-on-error path: whether the
     * run then pauses the step (Suspended Error, for fix + resume) or fails terminally, the step shows its error.
     * NOT recorded as a completed outcome, so on a resume / migrate the step re-runs rather than short-circuiting.
     */
    fun markError(stableId: ObjectStableId, message: String?) {
        emitStepTrace(stableId, StepTrace.State.Error, NullExecutionValue, message)
    }


    /**
     * Record a step's produced value (for downstream reference and live-edit capture) and mark it Done,
     * publishing its display value to the live trace. The raw value is kept verbatim for [referencedValue]; the
     * display falls back to a text rendering (matching the former step tracing).
     */
    fun markDone(stableId: ObjectStableId, value: Any?) {
        stepValues[stableId] = value
        completedOutcomes[stableId] = value
        emitStepTrace(stableId, StepTrace.State.Done, displayOf(value))
    }


    //----------------------------------------------------------------------------------- live-edit migration (§5)
    /** Seed the carried-over completed work from the predecessor run's capture (read once at run start). */
    fun restore(state: ScriptMigrationState) {
        restoredOutcomes.putAll(state.completedOutcomes)
        resultValue = state.result
    }


    /** Snapshot the run's completed work for carry-over at the migration barrier (see [ScriptMigrationState]). */
    fun captureState(): ScriptMigrationState {
        return ScriptMigrationState(LinkedHashMap(completedOutcomes), resultValue)
    }


    /** Whether this step completed in the predecessor run, so the spine can re-adopt it instead of re-running. */
    fun isReplayCompleted(stableId: ObjectStableId): Boolean {
        return restoredOutcomes.containsKey(stableId)
    }


    /**
     * Re-adopt a step's predecessor outcome on replay: record it for downstream reference + capture and re-emit
     * its Done trace, WITHOUT re-executing it. Returns the value so the enclosing sequence can yield it (a branch
     * / loop-body result).
     */
    fun adoptCompleted(stableId: ObjectStableId): Any? {
        val value = restoredOutcomes[stableId]
        stepValues[stableId] = value
        completedOutcomes[stableId] = value
        emitStepTrace(stableId, StepTrace.State.Done, displayOf(value))
        return value
    }


    /**
     * Drop the given ids from the replay set so they execute live — used by a loop that did NOT complete
     * pre-edit, so its body re-runs from the first iteration rather than short-circuiting on a body step's stale
     * last-iteration outcome.
     */
    fun dropReplay(stableIds: Iterable<ObjectStableId>) {
        for (stableId in stableIds) {
            restoredOutcomes.remove(stableId)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun setResult(value: TupleValue) {
        resultValue = value
    }


    fun result(): TupleValue? {
        return resultValue
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun emitStepTrace(
        stableId: ObjectStableId,
        state: StepTrace.State,
        display: ExecutionValue,
        error: String? = null
    ) {
        val trace = StepTrace(state, display, NullExecutionValue, error)
        execution.emit(Address.of(stableId.value), trace.asExecutionValue())
    }


    private fun displayOf(value: Any?): ExecutionValue {
        return ExecutionValue.of(value.toString())
    }
}
