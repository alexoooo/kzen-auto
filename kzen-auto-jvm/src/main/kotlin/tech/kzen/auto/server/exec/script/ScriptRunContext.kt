package tech.kzen.auto.server.exec.script

import tech.kzen.auto.common.objects.document.script.model.ScriptTree
import tech.kzen.auto.common.objects.document.script.model.ScriptValidation
import tech.kzen.auto.common.objects.document.script.model.StepTrace
import tech.kzen.auto.server.exec.LogicCompiler
import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.lib.common.exec.BinaryExecutionValue
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.NullExecutionValue
import tech.kzen.lib.common.exec.engine.Address
import tech.kzen.lib.common.exec.engine.ClosePolicy
import tech.kzen.lib.common.exec.engine.Execution
import tech.kzen.lib.common.exec.engine.Logic
import tech.kzen.lib.common.exec.engine.PauseReason
import tech.kzen.lib.common.exec.logic.ResourceClosePolicy
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.store.normal.ObjectStableId
import tech.kzen.lib.common.util.ExceptionUtils


/**
 * The [StepExecution] a Script's steps run against, plus the framework spine ([runSteps], the former MultiStep /
 * SequenceStep) that drives a step list: a [checkpoint] boundary before each step, the uniform per-step trace
 * lifecycle ("next to run" highlight, then Running, then Done with the produced value), pause-on-error via
 * [Execution.recoverable], and the live-edit replay short-circuit. Owned by the single [ScriptLogic] coroutine,
 * never shared, so it needs no synchronization.
 *
 * Trace emission mirrors the former MultiStep so the existing client display is unchanged: a step's live value
 * is a [StepTrace] addressed by its stable id, and the "next step to run" highlight is a reserved-address emit
 * ([nextStepAddressMarker]) the controller's trace bridge routes to
 * [tech.kzen.auto.common.objects.document.script.ScriptConventions.nextStepTracePath].
 *
 * LIVE-EDIT MIGRATION (logic-spec §5): as each step finishes its outcome is recorded in [completedOutcomes] (the
 * capture source); on the rebuilt run [restore] seeds [restoredOutcomes] from the predecessor's capture so the
 * spine can replay-short-circuit completed steps and a re-running loop can [dropReplay] its body's stale
 * outcomes. See [ScriptMigrationState] for the carried shape and its bounds.
 */
class ScriptRunContext(
    private val execution: Execution,
    private val structure: ScriptRunStructure,

    // The run-scoped resource handles (a browser) this Script shares with the Scripts it hosts — one instance
    // per top-level run, threaded to a hosted child in [host]. See [ScriptRunResources].
    private val resources: ScriptRunResources
): StepExecution {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // Reserved emit address marking the "next step to run" highlight, distinct from a step's own value
        // address (which is the step's stable id). The trace bridge recognizes it and routes to the fixed
        // next-step trace path; a stable id can never collide (it is an ObjectLocation string).
        const val nextStepAddressMarker = "\$next-step"
    }


    //----------------------------------------------------------------------------------------- per-Script structures
    override val scriptTree: ScriptTree get() = structure.scriptTree
    override val scriptValidation: ScriptValidation get() = structure.scriptValidation
    override val resultSignature: TupleDefinition get() = structure.resultSignature
    override val graphNotation: GraphNotation get() = structure.graphNotation

    private val objectStableMapper get() = structure.objectStableMapper


    //----------------------------------------------------------------------------------------------- per-run state
    // The live value graph downstream expressions reference (step outcomes + non-step bindings).
    private val stepValues = HashMap<ObjectStableId, Any?>()

    // The outcome each step that COMPLETED produced — the live-edit capture source. Excludes the non-step
    // bindings (a parameter / loop item), which the rebuilt run re-derives rather than carries.
    private val completedOutcomes = LinkedHashMap<ObjectStableId, Any?>()

    // The predecessor run's completed outcomes, seeded by [restore] across a live edit; consulted by the spine to
    // replay-short-circuit and pruned by a re-running loop ([dropReplay]). Empty on a fresh (non-migration) run.
    private val restoredOutcomes = HashMap<ObjectStableId, Any?>()

    // A RunStep's linked child Logic, compiled on demand and cached for this run (a RunStep in a loop reuses it).
    private val childLogics = HashMap<ObjectLocation, Logic>()

    private var resultValue: TupleValue? = null

    // The step the spine is currently running (whose trace [traceDetail] updates), and the detail it has
    // recorded so far — carried into the step's Done / Error trace so a screenshot persists past Running.
    // Saved / restored around each step so a nested branch (an If / loop body) doesn't clobber its parent's.
    private var currentStableId: ObjectStableId? = null
    private var currentDetail: ExecutionValue = NullExecutionValue


    //----------------------------------------------------------------------------------------- StepExecution: control
    override suspend fun checkpoint() {
        execution.checkpoint()
    }


    override suspend fun pauseHere() {
        execution.pauseHere(PauseReason.Explicit)
    }


    //------------------------------------------------------------------------------------------- StepExecution: values
    override fun referencedValue(location: ObjectLocation): Any? {
        val stableId = objectStableMapper.objectStableId(location)
        check(stepValues.containsKey(stableId)) { "No value produced for: $location" }
        return stepValues[stableId]
    }


    override fun argument(name: TupleComponentName): Any? {
        return execution.inputs.find(name)
    }


    override fun bind(location: ObjectLocation, value: Any?) {
        recordValue(objectStableMapper.objectStableId(location), value)
    }


    override fun setResult(value: TupleValue) {
        resultValue = value
    }


    //------------------------------------------------------------------------------------------ StepExecution: tracing
    override fun traceDetail(detail: ExecutionValue) {
        val stableId = currentStableId
            ?: error("No step is running")
        currentDetail = detail

        // Re-emit the running step's trace so the detail (a screenshot) shows immediately, before the step
        // completes; markDone then re-emits it on the Done trace so it persists.
        emitStepTrace(stableId, StepTrace.State.Running, NullExecutionValue, detail)

        // A binary detail (a screenshot) also joins the run's retained history film-strip, surviving loop
        // iterations and nested runs (the value-agnostic timeline — see [Execution.log]).
        if (detail is BinaryExecutionValue) {
            execution.log(detail)
        }
    }


    //------------------------------------------------------------------------------------------ StepExecution: resources
    override fun openResource(key: String, value: Any?, closePolicy: ResourceClosePolicy, closer: () -> Unit) {
        resources.put(key, value)
        execution.resource(key, closePolicy.toEnginePolicy()) {
            resources.remove(key)
            closer()
        }
    }


    override fun resource(key: String): Any? {
        return resources.get(key)
    }


    override fun releaseResource(key: String) {
        execution.releaseResource(key)
        resources.remove(key)
    }


    //-------------------------------------------------------------------------------------------- StepExecution: spine
    override suspend fun runSteps(steps: List<ObjectLocation>): Any? {
        var last: Any? = null
        for (stepLocation in steps) {
            val stableId = objectStableMapper.objectStableId(stepLocation)

            // Live-edit replay (logic-spec §5): a step that completed in the pre-edit run re-adopts its outcome
            // without re-executing — no "next to run" highlight, no checkpoint boundary, no work.
            if (restoredOutcomes.containsKey(stableId)) {
                last = adoptCompleted(stableId)
                continue
            }

            val step = scriptStepAt(stepLocation)
            publishNextStep(stableId)
            execution.checkpoint()

            // Track this step as the current one so its [traceDetail] (a screenshot) attributes to it and carries
            // into its Done / Error trace; saved / restored so a nested branch it runs doesn't clobber it.
            val previousStableId = currentStableId
            val previousDetail = currentDetail
            currentStableId = stableId
            try {
                // Pause-on-error (logic-spec §4): the engine renders the failure (Error trace) then, if
                // pause-on-error is on, parks the step Suspended(Error) for fix + resume and re-runs it on resume;
                // if off, the failure propagates and the run fails. markRunning is inside the recoverable unit so
                // each (re-)try repaints Running (and clears the prior try's detail). A failed step is never
                // markDone'd, so on resume / migrate it re-runs.
                last = execution.recoverable({ error ->
                    emitStepTrace(
                        stableId, StepTrace.State.Error, NullExecutionValue, currentDetail,
                        ExceptionUtils.message(error))
                }) {
                    currentDetail = NullExecutionValue
                    emitStepTrace(stableId, StepTrace.State.Running, NullExecutionValue, currentDetail)
                    step.run(this)
                }
                markDone(stableId, last)
            }
            finally {
                currentStableId = previousStableId
                currentDetail = previousDetail
            }
        }
        publishNextStep(null)
        return last
    }


    override fun dropReplay(steps: List<ObjectLocation>) {
        for (stableId in nestedStableIds(steps)) {
            restoredOutcomes.remove(stableId)
        }
    }


    //--------------------------------------------------------------------------------------------- StepExecution: host
    override suspend fun host(instructions: ObjectLocation, arguments: TupleValue): TupleValue {
        val child = childLogics.getOrPut(instructions) {
            LogicCompiler.compile(
                instructions, structure.graphNotation, structure.graphDefinition, structure.services)
        }

        // Share this run's resource registry with a hosted child Script so a browser opened here is the same
        // browser the callee drives (a Flow / Job child has no Script resources to inherit).
        if (child is ScriptLogic) {
            child.inheritResources(resources)
        }

        // The hosting RunStep ([currentStableId], set by the spine before it invoked this step) is the child's
        // call-site: recording it on the child node lets the trace store attribute the child's execution to
        // THIS RunStep, so its screenshot strip scopes to the invocations it spawned (distinguishing two
        // RunSteps that host the same sub-Script document).
        return execution.host(
            objectStableMapper.objectStableId(instructions), child, arguments, currentStableId)
    }


    //----------------------------------------------------------------------------------- run-internal (for ScriptLogic)
    /** Record a parameter / binding value by stable id (no trace) — used by [ScriptLogic] at run start. */
    fun recordValue(stableId: ObjectStableId, value: Any?) {
        stepValues[stableId] = value
    }


    fun result(): TupleValue? {
        return resultValue
    }


    /** Seed the carried-over completed work from the predecessor run's capture (read once at run start). */
    fun restore(state: ScriptMigrationState) {
        restoredOutcomes.putAll(state.completedOutcomes)
        resultValue = state.result
    }


    /** Snapshot the run's completed work for carry-over at the migration barrier (see [ScriptMigrationState]). */
    fun captureState(): ScriptMigrationState {
        return ScriptMigrationState(LinkedHashMap(completedOutcomes), resultValue)
    }


    //----------------------------------------------------------------------------------------------------- internals
    private fun scriptStepAt(location: ObjectLocation): ScriptStep {
        return structure.graphInstance[location]?.reference as? ScriptStep
            ?: error("Not a ScriptStep: $location")
    }


    // This step's stable id plus those of every step nested within it (an If's branches, a loop's body),
    // recursing through each step's declared [ScriptStep.nestedStepLists] — generic, with no per-type knowledge.
    private fun nestedStableIds(locations: List<ObjectLocation>): List<ObjectStableId> {
        val result = ArrayList<ObjectStableId>()
        for (location in locations) {
            result.add(objectStableMapper.objectStableId(location))
            for (nestedList in scriptStepAt(location).nestedStepLists()) {
                result.addAll(nestedStableIds(nestedList))
            }
        }
        return result
    }


    private fun adoptCompleted(stableId: ObjectStableId): Any? {
        val value = restoredOutcomes[stableId]
        stepValues[stableId] = value
        completedOutcomes[stableId] = value
        emitStepTrace(stableId, StepTrace.State.Done, displayOf(value))
        return value
    }


    private fun markDone(stableId: ObjectStableId, value: Any?) {
        stepValues[stableId] = value
        completedOutcomes[stableId] = value
        emitStepTrace(stableId, StepTrace.State.Done, displayOf(value), currentDetail)
    }


    private fun publishNextStep(stableId: ObjectStableId?) {
        val value = if (stableId == null) NullExecutionValue else ExecutionValue.of(stableId.value)
        execution.emit(Address.of(nextStepAddressMarker), value)
    }


    private fun emitStepTrace(
        stableId: ObjectStableId,
        state: StepTrace.State,
        display: ExecutionValue,
        detail: ExecutionValue = NullExecutionValue,
        error: String? = null
    ) {
        val trace = StepTrace(state, display, detail, error)
        execution.emit(Address.of(stableId.value), trace.asExecutionValue())
    }


    private fun displayOf(value: Any?): ExecutionValue {
        return ExecutionValue.of(value.toString())
    }


    // Map the notation-level close policy an opening step declares to the engine's resource close policy.
    private fun ResourceClosePolicy.toEnginePolicy(): ClosePolicy {
        return when (this) {
            ResourceClosePolicy.Auto -> ClosePolicy.Auto
            ResourceClosePolicy.Manual -> ClosePolicy.Manual
            ResourceClosePolicy.KeepOnFailure -> ClosePolicy.KeepOnFailure
        }
    }
}
