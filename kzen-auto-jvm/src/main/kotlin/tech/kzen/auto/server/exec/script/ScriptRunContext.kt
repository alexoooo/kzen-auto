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
    private val stepValues = HashMap<ObjectStableId, Any?>()
    private var resultValue: TupleValue? = null


    //-----------------------------------------------------------------------------------------------------------------
    /** Resolve the value a predecessor step (or loop-item / parameter binding) produced. */
    fun referencedValue(stableId: ObjectStableId): Any? {
        check(stepValues.containsKey(stableId)) { "No value produced for: $stableId" }
        return stepValues[stableId]
    }


    /**
     * Record a value for downstream reference WITHOUT emitting a step-trace entry — for the non-step bindings
     * (a parameter, a loop item) that are resolved by reference but never shown as steps in the spine.
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
     * Record a step's produced value (for downstream reference) and mark it Done, publishing its display value
     * to the live trace. The raw value is kept verbatim for [referencedValue]; the display falls back to a text
     * rendering (matching the former step tracing).
     */
    fun markDone(stableId: ObjectStableId, value: Any?) {
        stepValues[stableId] = value
        emitStepTrace(stableId, StepTrace.State.Done, displayOf(value))
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun setResult(value: TupleValue) {
        resultValue = value
    }


    fun result(): TupleValue? {
        return resultValue
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun emitStepTrace(stableId: ObjectStableId, state: StepTrace.State, display: ExecutionValue) {
        val trace = StepTrace(state, display, NullExecutionValue, null)
        execution.emit(Address.of(stableId.value), trace.asExecutionValue())
    }


    private fun displayOf(value: Any?): ExecutionValue {
        return ExecutionValue.of(value.toString())
    }
}
