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
import tech.kzen.lib.common.exec.engine.ResourceScope
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
 * is a [StepTrace] addressed by its stable id. The "next step to run" highlight is engine-owned position: the
 * per-step boundary names its step ([Execution.checkpoint]'s `at`), the engine records it as the node's
 * position, and the client reads it off the run frame in LogicStatus.
 *
 * LIVE-EDIT MIGRATION (logic-spec §5): as each step finishes its outcome is recorded in [completedOutcomes] (the
 * capture source); on the rebuilt run [restore] seeds [restoredOutcomes] from the predecessor's capture so the
 * spine can replay-short-circuit completed steps and a re-running loop can [dropReplay] its body's stale
 * outcomes. A mid-flight step (a loop between iterations) additionally carries opaque sub-state ([carryStates] /
 * [restoredCarries], via [recordCarry] / [restoredCarry]) so it can resume where it left off — a loop at its
 * current iteration. See [ScriptMigrationState] for the carried shape and its bounds.
 */
class ScriptRunContext(
    private val execution: Execution,
    private val structure: ScriptRunStructure
): StepExecution {
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

    // Opaque per-step mid-flight migration sub-state ([StepExecution.recordCarry]) — a loop's iteration cursor —
    // carried alongside [completedOutcomes]: [carryStates] is the live capture source, [restoredCarries] the
    // predecessor run's carries seeded by [restore] (read via [StepExecution.restoredCarry], pruned by [dropReplay]).
    private val carryStates = LinkedHashMap<ObjectStableId, Any?>()
    private val restoredCarries = HashMap<ObjectStableId, Any?>()

    // A RunStep's linked child Logic, compiled on demand and cached for this run (a RunStep in a loop reuses it).
    private val childLogics = HashMap<ObjectLocation, Logic>()

    // Per-run memo backing [perRunSingleton] — a compiled-expression instance reused across a loop's iterations,
    // keyed by content signature. Confined to the run coroutine, so no locking.
    private val perRunSingletons = HashMap<String, Any>()

    private var resultValue: TupleValue? = null

    // The step the spine is currently running (whose trace [traceDetail] / [traceNote] updates), and the
    // detail + note it has recorded so far — carried into the step's Done / Error trace so a screenshot
    // (and its diagnostic note) persists past Running.
    // Saved / restored around each step so a nested branch (an If / loop body) doesn't clobber its parent's.
    private var currentStableId: ObjectStableId? = null
    private var currentDetail: ExecutionValue = NullExecutionValue
    private var currentNote: String? = null


    //----------------------------------------------------------------------------------------- StepExecution: control
    override suspend fun checkpoint() {
        execution.checkpoint()
    }


    override suspend fun pauseHere() {
        execution.pauseHere(PauseReason.Explicit)
    }


    @Suppress("UNCHECKED_CAST")
    override fun <T: Any> perRunSingleton(key: String, factory: () -> T): T {
        return perRunSingletons.getOrPut(key) { factory() } as T
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


    //------------------------------------------------------------------------------------------- StepExecution: carry
    override fun recordCarry(location: ObjectLocation, state: Any?) {
        val stableId = objectStableMapper.objectStableId(location)
        if (state == null) {
            carryStates.remove(stableId)
        }
        else {
            carryStates[stableId] = state
        }
    }


    override fun restoredCarry(location: ObjectLocation): Any? {
        return restoredCarries[objectStableMapper.objectStableId(location)]
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


    override fun traceNote(note: String) {
        val stableId = currentStableId
            ?: error("No step is running")
        currentNote = note

        // Re-emit the running step's trace so the note shows immediately; markDone then re-emits it
        // on the Done trace so it persists.
        emitStepTrace(stableId, StepTrace.State.Running, NullExecutionValue, currentDetail)
    }


    //------------------------------------------------------------------------------------------ StepExecution: resources
    // Delegated wholly to the engine, which stores the live handle with the registration: reading walks the
    // ancestor chain (so a hosted child — Script, Flow, or Job — borrows the handle its host opened), and the
    // registration survives a live edit with its owning frame's stable identity (logic-spec §5/§6).
    override fun openResource(key: String, value: Any?, closePolicy: ResourceClosePolicy, closer: () -> Unit) {
        val (scope, enginePolicy) = closePolicy.toEngine()
        execution.resource(key, enginePolicy, scope, value, closer)
    }


    override fun resource(key: String): Any? {
        return execution.resourceValue(key)
    }


    override fun releaseResource(key: String) {
        execution.releaseResource(key)
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
            execution.checkpoint(stableId)

            // Track this step as the current one so its [traceDetail] (a screenshot) and [traceNote] attribute
            // to it and carry into its Done / Error trace; saved / restored so a nested branch it runs doesn't
            // clobber it.
            val previousStableId = currentStableId
            val previousDetail = currentDetail
            val previousNote = currentNote
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
                    currentNote = null
                    emitStepTrace(stableId, StepTrace.State.Running, NullExecutionValue, currentDetail)
                    step.run(this)
                }
                markDone(stableId, last)
            }
            finally {
                currentStableId = previousStableId
                currentDetail = previousDetail
                currentNote = previousNote
            }
        }
        return last
    }


    // The generic iteration reset (see the [StepExecution.dropReplay] contract): beyond the replay set, also
    // prunes the capture source — so a mid-iteration capture carries only the current iteration's completed
    // prefix — and the restored carries, so a nested loop's cursor from a different enclosing iteration is
    // never consumed by a later fresh pass. [stepValues] (the live value graph) is deliberately untouched.
    // The engine-side discard tells the run that hosted-child invocations launched from these steps (a
    // RunStep in the loop body) are abandoned — a fresh invocation must not adopt the pre-edit one's
    // migration capture (logic-spec §5 "invocation identity").
    override fun dropReplay(steps: List<ObjectLocation>) {
        val stableIds = nestedStableIds(steps)
        for (stableId in stableIds) {
            restoredOutcomes.remove(stableId)
            restoredCarries.remove(stableId)
            completedOutcomes.remove(stableId)
        }
        execution.discardCaptured(stableIds)
    }


    //--------------------------------------------------------------------------------------------- StepExecution: host
    override suspend fun host(instructions: ObjectLocation, arguments: TupleValue): TupleValue {
        val child = childLogics.getOrPut(instructions) {
            LogicCompiler.compile(
                instructions, structure.graphNotation, structure.graphDefinition, structure.services)
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
        restoredCarries.putAll(state.stepCarry)
        resultValue = state.result
    }


    /** Snapshot the run's completed work for carry-over at the migration barrier (see [ScriptMigrationState]). */
    fun captureState(): ScriptMigrationState {
        return ScriptMigrationState(LinkedHashMap(completedOutcomes), LinkedHashMap(carryStates), resultValue)
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


    private fun emitStepTrace(
        stableId: ObjectStableId,
        state: StepTrace.State,
        display: ExecutionValue,
        detail: ExecutionValue = NullExecutionValue,
        error: String? = null
    ) {
        // NB: currentNote belongs to the step the spine is currently running; every emit site except
        // [adoptCompleted] (which emits for a replayed step while the note is the parent's) targets it.
        val note = if (stableId == currentStableId) currentNote else null
        val trace = StepTrace(state, display, detail, error, note)
        execution.emit(Address.of(stableId.value), trace.asExecutionValue())
    }


    private fun displayOf(value: Any?): ExecutionValue {
        return ExecutionValue.of(value.toString())
    }


    // Decompose the notation-level close policy an opening step declares into the engine's two orthogonal
    // primitives: which node owns the resource (ResourceScope) and how that node's settle disposes it (ClosePolicy).
    private fun ResourceClosePolicy.toEngine(): Pair<ResourceScope, ClosePolicy> {
        return when (this) {
            ResourceClosePolicy.Auto -> ResourceScope.Self to ClosePolicy.Auto
            ResourceClosePolicy.Manual -> ResourceScope.Self to ClosePolicy.Manual
            ResourceClosePolicy.KeepOnFailure -> ResourceScope.Self to ClosePolicy.KeepOnFailure
            ResourceClosePolicy.ParentDocument -> ResourceScope.Parent to ClosePolicy.Auto
            ResourceClosePolicy.ParentDocumentKeepOnFailure -> ResourceScope.Parent to ClosePolicy.KeepOnFailure
            ResourceClosePolicy.Run -> ResourceScope.Root to ClosePolicy.Auto
            ResourceClosePolicy.RunKeepOnFailure -> ResourceScope.Root to ClosePolicy.KeepOnFailure
        }
    }
}
