package tech.kzen.auto.server.exec.script

import tech.kzen.auto.common.objects.document.logic.context.ContextDescriptor
import tech.kzen.auto.common.objects.document.script.model.ScriptTree
import tech.kzen.auto.common.objects.document.script.model.ScriptValidation
import tech.kzen.auto.common.objects.document.script.model.StepTrace
import tech.kzen.auto.common.util.TraceDisplay
import tech.kzen.auto.server.exec.LogicCompiler
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.objects.script.api.ScriptControlSignal
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.lib.common.exec.BinaryExecutionValue
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.NullExecutionValue
import tech.kzen.lib.common.exec.engine.Address
import tech.kzen.lib.common.exec.engine.Execution
import tech.kzen.lib.common.exec.engine.Logic
import tech.kzen.lib.common.exec.engine.PauseReason
import tech.kzen.lib.common.exec.engine.disposal.SettleDisposalPolicy
import tech.kzen.lib.common.exec.logic.ResourceClosePolicy
import tech.kzen.lib.common.exec.data.binding.BindingName
import tech.kzen.lib.common.exec.data.binding.BindingSchema
import tech.kzen.lib.common.exec.data.binding.BindingState
import tech.kzen.lib.common.exec.data.binding.DataBindings
import tech.kzen.lib.common.exec.data.value.DataSnapshot
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.SnapshotPolicy
import tech.kzen.lib.common.exec.data.value.SnapshotResult
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
 * LIVE-EDIT MIGRATION (logic-spec §5) and MOVE-TO (Set Next Statement, logic-spec §4) bookkeeping — the
 * completed-outcome capture, the restored replay sets, the jump surgery and the descend obligations — is
 * owned by [ScriptReplayState] (seeded via [restore], snapshot via [captureState]); the spine consults it to
 * replay-short-circuit completed steps, skip / descend for a repositioning, and resume a mid-flight loop at
 * its carried cursor. The typed context API ([StepExecution]'s bind / read / release / uses-gate surface) is
 * resolved by [ScriptStepContexts].
 *
 * CONTROL FLOW: a step may raise a [ScriptControlSignal] (continue / break / return) via [raiseControlSignal];
 * the spine ([runSteps]) short-circuits on it and a loop ([consumeLoopSignal]) or the root
 * ([consumeRootSignalOrFail]) consumes it within the SAME engine release — so a signal is never captured by
 * [captureState] nor migrated. That is why an End Script that terminates the run needs no per-step capture: the
 * run goes terminal (no park), and a terminal run is never replayed.
 */
class ScriptRunContext(
    private val execution: Execution,
    private val structure: ScriptRunStructure
): StepExecution {
    //----------------------------------------------------------------------------------------- per-Script structures
    override val scriptTree: ScriptTree get() = structure.scriptTree
    override val scriptValidation: ScriptValidation get() = structure.scriptValidation
    override val resultSignature: BindingSchema get() = structure.resultSignature
    override val graphNotation: GraphNotation get() = structure.graphNotation

    private val objectStableMapper get() = structure.objectStableMapper


    //----------------------------------------------------------------------------------------------- per-run state
    // The live value graph downstream expressions reference (step outcomes + non-step bindings).
    private val stepValues = HashMap<ObjectStableId, DataValue?>()

    // The replay / migration / move-to bookkeeping (see [ScriptReplayState]) and the typed context
    // resolution (see [ScriptStepContexts]) — both run-confined like this orchestrator.
    private val replay = ScriptReplayState(structure)
    private val stepContexts = ScriptStepContexts(execution, structure.graphNotation) { currentStepLocation }

    // A RunStep's linked child Logic, compiled on demand and cached for this run (a RunStep in a loop reuses it).
    private val childLogics = HashMap<ObjectLocation, Logic>()

    // Per-run memo backing [perRunSingleton] — a compiled-expression instance reused across a loop's iterations,
    // keyed by content signature. Confined to the run coroutine, so no locking.
    private val perRunSingletons = HashMap<String, Any>()

    private var resultValue: DataBindings? = null

    // A pending control-flow completion signal (continue/break/return — see [ScriptControlSignal]) and the stable
    // id of the step that raised it. Release-local: the spine short-circuits on it and a loop / the root consumes
    // it within the same engine release, so it is never captured by [captureState] nor migrated.
    private var pendingSignal: ScriptControlSignal? = null
    private var pendingSignalRaisedBy: ObjectStableId? = null

    // The step the spine is currently running (whose trace [traceDetail] / [traceNote] updates), and the
    // detail + note it has recorded so far — carried into the step's Done / Error trace so a screenshot
    // (and its diagnostic note) persists past Running.
    // Saved / restored around each step so a nested branch (an If / loop body) doesn't clobber its parent's.
    private var currentStableId: ObjectStableId? = null
    private var currentDetail: ExecutionValue = NullExecutionValue
    private var currentNote: String? = null

    // The running step's own location, tracked alongside [currentStableId] (and saved / restored the same
    // way): the typed context API ([ScriptStepContexts]) resolves the step's `binds` / `uses` / `releases`
    // from NOTATION, and the stable id alone cannot address it.
    private var currentStepLocation: ObjectLocation? = null


    //----------------------------------------------------------------------------------------- StepExecution: control
    override suspend fun checkpoint() {
        execution.checkpoint()
    }


    override suspend fun pauseHere() {
        execution.pauseHere(PauseReason.Explicit)
    }


    override suspend fun <R> blocking(block: () -> R): R {
        return execution.blocking(block)
    }


    @Suppress("UNCHECKED_CAST")
    override fun <T: Any> perRunSingleton(key: String, factory: () -> T): T {
        return perRunSingletons.getOrPut(key) { factory() } as T
    }


    //------------------------------------------------------------------------------------------- StepExecution: values
    override fun referencedValue(location: ObjectLocation): Any? {
        val stableId = objectStableMapper.objectStableId(location)
        check(stepValues.containsKey(stableId)) { "No value produced for: $location" }
        return stepValues[stableId]?.let(JobDataValues::boundary)
    }


    override fun isValueReferenced(location: ObjectLocation): Boolean {
        return location in structure.valueReferencedSteps
    }


    override fun argument(name: BindingName): Any? {
        return when (val state = execution.inputs[name]) {
            BindingState.Unbound -> null
            is BindingState.Bound -> JobDataValues.boundary(state.value)
        }
    }


    override fun recordValue(location: ObjectLocation, value: Any?) {
        recordValue(
            objectStableMapper.objectStableId(location),
            structure.liftStepResult(location, value))
    }


    override fun setResult(value: DataBindings) {
        resultValue = value
    }


    //------------------------------------------------------------------------------------ StepExecution: control flow
    override fun raiseControlSignal(signal: ScriptControlSignal) {
        check(currentStableId != null) { "No step is running" }
        pendingSignal = signal
        pendingSignalRaisedBy = currentStableId
    }


    override fun consumeLoopSignal(selfLocation: ObjectLocation): ScriptControlSignal? {
        val signal = pendingSignal
        val target = when (signal) {
            is ScriptControlSignal.SkipIteration -> signal.target
            is ScriptControlSignal.FinishLoop -> signal.target
            else -> return null  // no signal, or EndScript (never loop-consumed)
        }
        if (objectStableMapper.objectStableId(target) == objectStableMapper.objectStableId(selfLocation)) {
            pendingSignal = null
            pendingSignalRaisedBy = null
            return signal
        }
        return null
    }


    override fun pendingControlSignal(): ScriptControlSignal? {
        return pendingSignal
    }


    //------------------------------------------------------------------------------------------- StepExecution: carry
    override fun recordCarry(location: ObjectLocation, state: Any?) {
        replay.recordCarry(objectStableMapper.objectStableId(location), state)
    }


    override fun restoredCarry(location: ObjectLocation): Any? {
        return replay.restoredCarry(objectStableMapper.objectStableId(location))
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
        // iterations and nested runs (the value-agnostic timeline — see [Execution.log]). This is the ONLY
        // retained copy: the trace emits above are transient (see [emitStepTrace]), so a screenshot is stored
        // once rather than once per emit that happens to carry it as its detail.
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


    //------------------------------------------------------------------------------------------- StepExecution: context
    // The typed context API, delegated wholly to [ScriptStepContexts] (which scopes every argument-free
    // resolution to [currentStepLocation]'s declarations).
    override fun declaredContexts(): List<ContextDescriptor> {
        return stepContexts.declaredContexts()
    }


    override fun bindContext(value: Any?, qualifier: String?) {
        stepContexts.bindContext(value, qualifier)
    }


    override fun bindContext(
        value: Any?,
        closePolicy: ResourceClosePolicy,
        qualifier: String?,
        closer: () -> Unit
    ) {
        stepContexts.bindContext(value, closePolicy, qualifier, closer)
    }


    override fun disposeAtSettle(policy: SettleDisposalPolicy, closer: () -> Unit) {
        // No key, no conformance check and no step-declaration lookup — there is nothing to name. The
        // registration belongs to the frame, not to the step, so it outlives the step that made it and is
        // invisible to every context read.
        execution.onSettle(policy, closer)
    }


    override fun contextValue(context: ObjectLocation?, qualifier: String?): Any {
        return stepContexts.contextValue(context, qualifier)
    }


    override fun contextValueOrNull(context: ObjectLocation?, qualifier: String?): Any? {
        return stepContexts.contextValueOrNull(context, qualifier)
    }


    override fun releaseContext(context: ObjectLocation?, qualifier: String?) {
        stepContexts.releaseContext(context, qualifier)
    }


    //------------------------------------------------------------------------------------------ StepExecution: resources
    // Delegated wholly to the engine, which stores the live handle with the registration: reading walks the
    // ancestor chain (so a hosted child — Script, Flow, or Job — borrows the handle its host opened), and the
    // registration survives a live edit with its owning frame's stable identity (logic-spec §5/§6).
    //
    // On the engine's raw string interop layer rather than the typed one, and that is a supported layer now
    // rather than deprecated debt: a plugin-supplied key is arbitrary text known only at run time, which is the
    // case the layer exists for. It inherits the layer's strict-to-write / permissive-to-address split —
    // opening under a string no key could be spelled as throws, while reading or releasing one addresses
    // nothing and answers null / no-op.
    override fun openResource(key: String, value: Any?, closePolicy: ResourceClosePolicy, closer: () -> Unit) {
        execution.resource(key, closePolicy.toEngine(), value, closer)
    }


    override fun resource(key: String): Any? {
        return execution.resourceValue(key)
    }


    override fun releaseResource(key: String) {
        execution.releaseResource(key)
    }


    //-------------------------------------------------------------------------------------------- StepExecution: spine
    override suspend fun runSteps(steps: List<ObjectLocation>): Any? {
        var last: DataValue? = null
        for (stepLocation in steps) {
            val stableId = objectStableMapper.objectStableId(stepLocation)

            // Move-to (Set Next Statement) forward-skip: the rebuilt spine walked past this step to reach a later
            // target, so it produces NO value (a later reference to it error-parks via [referencedValue]) — no
            // checkpoint, no outcome. `last` is left untouched (a skipped step contributes nothing).
            if (replay.isSkipped(stableId)) {
                emitStepTrace(stableId, StepTrace.State.Skipped, NullExecutionValue)
                continue
            }

            // Live-edit replay (logic-spec §5): a step that completed in the pre-edit run re-adopts its outcome
            // without re-executing — no "next to run" highlight, no checkpoint boundary, no work.
            if (replay.hasRestoredOutcome(stableId)) {
                last = adoptCompleted(stepLocation, stableId)
                continue
            }

            val step = structure.scriptStepAt(stepLocation)

            // Move-to descend: an ancestor of the jump target runs (an If re-evaluates its condition) but its
            // checkpoint is suppressed (claim-once), so the paused rebuild parks at the target inside its branch,
            // not at the ancestor's own boundary. Ordinary steps always take the boundary.
            val suppressBoundary = replay.consumeDescend(stableId)
            if (!suppressBoundary) {
                execution.checkpoint(stableId)
            }

            // Track this step as the current one so its [traceDetail] (a screenshot) and [traceNote] attribute
            // to it and carry into its Done / Error trace; saved / restored so a nested branch it runs doesn't
            // clobber it.
            val previousStableId = currentStableId
            val previousStepLocation = currentStepLocation
            val previousDetail = currentDetail
            val previousNote = currentNote
            currentStableId = stableId
            currentStepLocation = stepLocation
            try {
                // Pause-on-error (logic-spec §4): the engine renders the failure (Error trace) then, if
                // pause-on-error is on, parks the step Suspended(Error) for fix + resume and re-runs it on resume;
                // if off, the failure propagates and the run fails. markRunning is inside the recoverable unit so
                // each (re-)try repaints Running (and clears the prior try's detail). A failed step is never
                // markDone'd, so on resume / migrate it re-runs.
                val rawResult = execution.recoverable({ error ->
                    // A step that errored after raising a signal re-raises on its successful retry, so a failed
                    // try must never leave a signal pending across the resulting park (no signal coexists with a
                    // park — the release-local invariant).
                    pendingSignal = null
                    pendingSignalRaisedBy = null
                    emitStepTrace(
                        stableId, StepTrace.State.Error, NullExecutionValue, currentDetail,
                        ExceptionUtils.message(error))
                }) {
                    currentDetail = NullExecutionValue
                    currentNote = null
                    emitStepTrace(stableId, StepTrace.State.Running, NullExecutionValue, currentDetail)
                    stepContexts.checkUsedContexts(stepLocation)
                    step.run(this)
                }
                last = structure.liftStepResult(stepLocation, rawResult)

                // Control flow (continue/break/return — see [ScriptControlSignal]): after the step runs, a pending
                // signal short-circuits the walk. A CONTAINER the signal merely passed through (an If, or a loop
                // propagating an outer signal) gets a Done trace NAMING the signal but no completed-outcome entry
                // (a container's value is never referenced from outside its scope, so skipping markDone is safe);
                // the step that RAISED the signal gets its normal Done. Either way the remaining steps do not run —
                // a loop ([consumeLoopSignal]) or the root ([consumeRootSignalOrFail]) consumes the signal.
                val signal = pendingSignal
                if (signal != null && pendingSignalRaisedBy != stableId) {
                    emitStepTrace(stableId, StepTrace.State.Done, displayTextOf(signalDisplay(signal)), currentDetail)
                    return last?.let(JobDataValues::boundary)
                }
                markDone(stableId, last)
                if (signal != null) {
                    return last?.let(JobDataValues::boundary)
                }
            }
            finally {
                currentStableId = previousStableId
                currentStepLocation = previousStepLocation
                currentDetail = previousDetail
                currentNote = previousNote
            }
        }
        return last?.let(JobDataValues::boundary)
    }


    // The generic iteration reset (see the [StepExecution.dropReplay] contract): the map half is
    // [ScriptReplayState.dropReplay]; [stepValues] (the live value graph) is deliberately untouched.
    // The engine-side discard tells the run that hosted-child invocations launched from these steps (a
    // RunStep in the loop body) are abandoned — a fresh invocation must not adopt the pre-edit one's
    // migration capture (logic-spec §5 "invocation identity"). The engine-side reset is the OBSERVABLE half:
    // the steps' emitted traces (the addresses mirror [emitStepTrace]) and the retained trace values of the
    // hosted invocations their RunSteps launched clear, so the fresh pass presents a fresh trace while the
    // film-strip history survives (logic-spec §7 resettable live state).
    override fun dropReplay(steps: List<ObjectLocation>) {
        val stableIds = nestedStableIds(steps)
        replay.dropReplay(stableIds)
        execution.discardCaptured(stableIds)
        execution.resetEmitted(stableIds.map { Address.of(it.value) }, stableIds)
    }


    //--------------------------------------------------------------------------------------------- StepExecution: host
    override suspend fun host(instructions: ObjectLocation, arguments: DataBindings): DataBindings {
        val child = childLogics.getOrPut(instructions) {
            LogicCompiler.compile(
                instructions, structure.graphNotation, structure.graphDefinition, structure.services)
        }

        // The hosting RunStep ([currentStableId], set by the spine before it invoked this step) is the child's
        // call-site: recording it on the child node lets the trace store attribute the child's execution to
        // THIS RunStep, so its screenshot strip scopes to the invocations it spawned (distinguishing two
        // RunSteps that host the same sub-Script document).
        val bindingChild = child as? Logic
            ?: error("Child Logic is not binding-native: $instructions")
        val childInputs = bindingChild.signature().inputs
        val supplied = arguments.entries().mapNotNull { (definition, state) ->
            val childDefinition = childInputs.find(definition.name)
            require(childDefinition != null) {
                "Unknown child argument '${definition.name}' for $instructions"
            }
            val bound = state as? BindingState.Bound ?: return@mapNotNull null
            definition.name to JobDataValues.lift(
                JobDataValues.boundary(bound.value),
                childDefinition.contract)
        }
        val normalized = DataBindings.bind(childInputs, supplied)
        return execution.host(
            objectStableMapper.objectStableId(instructions), child, normalized, currentStableId,
            initialBindings = stepContexts.callSiteBindings())
    }


    //----------------------------------------------------------------------------------- run-internal (for ScriptLogic)
    /**
     * Record a parameter / binding value by stable id in the live value graph, with NO trace emit (see the
     * [tech.kzen.auto.server.objects.script.api.StepExecution.recordValue] contract — a loop-item binding is
     * re-bound every iteration and nothing reads its address; this is the VALUE GRAPH, not the ambient scope
     * [tech.kzen.auto.server.objects.script.api.StepExecution.bindContext] writes to). A parameter's display value is surfaced
     * separately, once at run start, by [ScriptLogic] via [tech.kzen.auto.server.exec.LogicParameterTrace].
     */
    fun recordValue(stableId: ObjectStableId, value: DataValue?) {
        stepValues[stableId] = value
    }


    fun result(): DataBindings? {
        return resultValue
    }


    /**
     * Root-level control-signal disposition, called by [ScriptLogic] after the root [runSteps]:
     * [ScriptControlSignal.EndScript] is the intended terminator (return semantics — consumed here); a Skip /
     * Finish reaching the root has no enclosing loop to consume it (a mistargeted control step), so fail loudly
     * (ControlStep validation should make this unreachable).
     */
    fun consumeRootSignalOrFail() {
        when (val signal = pendingSignal) {
            null, ScriptControlSignal.EndScript -> {
                pendingSignal = null
                pendingSignalRaisedBy = null
            }
            is ScriptControlSignal.SkipIteration, is ScriptControlSignal.FinishLoop ->
                error("Loop control signal reached the Script root without an enclosing loop " +
                        "(mistargeted control step): $signal")
        }
    }


    /**
     * Seed the carried-over completed work — including any move-to jump surgery — from the predecessor run's
     * capture, read once at run start: see [ScriptReplayState.restore], which owns the whole disposition.
     */
    fun restore(
        state: ScriptMigrationState?,
        moveTarget: ObjectStableId?,
        moveDescendCallSite: ObjectStableId?,
        removedStableIds: Set<ObjectStableId>
    ) {
        resultValue = replay.restore(
            state, moveTarget, moveDescendCallSite, removedStableIds,
            discardCaptured = { execution.discardCaptured(it) },
            emitIdle = { emitStepTrace(it, StepTrace.State.Idle, NullExecutionValue) })
    }


    /** Snapshot the run's completed work for carry-over at the migration barrier (see [ScriptMigrationState]). */
    fun captureState(): ScriptMigrationState {
        return replay.captureState(resultValue)
    }


    //----------------------------------------------------------------------------------------------------- internals


    // This step's stable id plus those of every step nested within it (an If's branches, a loop's body),
    // recursing through each step's declared [ScriptStep.nestedStepLists] — generic, with no per-type knowledge.
    private fun nestedStableIds(locations: List<ObjectLocation>): List<ObjectStableId> {
        val result = ArrayList<ObjectStableId>()
        for (location in locations) {
            result.add(objectStableMapper.objectStableId(location))
            for (nestedList in structure.scriptStepAt(location).nestedStepLists(structure.graphNotation)) {
                result.addAll(nestedStableIds(nestedList))
            }
        }
        return result
    }


    // Adopting a CONTAINER (an If, a loop) adopts the steps nested within it too. Their outcomes were carried
    // alongside their container's, but adopting it means its own [runSteps] over them never runs — so without
    // this descent the rebuilt display would show a Done container above a subtree repainted Idle, and a
    // reference to a branch-internal step would find no value in [stepValues] and error-park.
    // Only nested steps the carry actually holds are adopted: a branch the run never took, or a loop-body
    // iteration [dropReplay] reset, has no outcome and stays Idle — which is what was on screen before the edit.
    private fun adoptCompleted(stepLocation: ObjectLocation, stableId: ObjectStableId): DataValue? {
        for (nestedStepList in structure.scriptStepAt(stepLocation).nestedStepLists(structure.graphNotation)) {
            for (nestedLocation in nestedStepList) {
                val nestedStableId = objectStableMapper.objectStableId(nestedLocation)
                if (replay.hasRestoredOutcome(nestedStableId)) {
                    adoptCompleted(nestedLocation, nestedStableId)
                }
            }
        }
        return adoptOutcome(stableId)
    }


    private fun adoptOutcome(stableId: ObjectStableId): DataValue? {
        val value = replay.restoredOutcome(stableId)
        stepValues[stableId] = value
        replay.recordCompleted(stableId, value)
        emitStepTrace(
            stableId, StepTrace.State.Done, displayOf(value),
            replay.partialDetailOrNull(stableId) ?: NullExecutionValue)
        return value
    }


    private fun markDone(stableId: ObjectStableId, value: DataValue?) {
        stepValues[stableId] = value
        replay.recordCompleted(stableId, value)
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
        // [adoptOutcome] (which emits for a replayed step while the note is the parent's) targets it.
        val note = if (stableId == currentStableId) currentNote else null
        val trace = StepTrace(state, display, detail, error, note)

        // TRANSIENT (logic-spec §7 retention-vs-bounding): a StepTrace is the step's CURRENT state, and the live
        // latest-value-per-address view is the only thing that ever reads it — trace queries project the engine's
        // live map ([RunEngineLogicTrace]), while the append-only history serves the film strip alone (its
        // log-style events). Retaining these would grow history without bound and without a reader: a loop emits
        // ~2 events per body step per iteration, and a screenshot step would store its binary a second and third
        // time (inside the Running and Done traces) beside the one [execution.log] copy that is actually read.
        execution.emit(Address.of(stableId.value), trace.asExecutionValue(), retain = false)
    }


    // Human-facing only, and bounded: the value graph ([stepValues]) keeps the whole value, so downstream
    // expressions and the Script's result are unaffected — this caps just the string that reaches the trace,
    // the wire, and every client poll.
    private fun displayOf(value: DataValue?): ExecutionValue {
        if (value == null) {
            return NullExecutionValue
        }
        val snapshot = DataSnapshot.capture(value, SnapshotPolicy(
            maximumDepth = 16,
            maximumElements = 256,
            maximumTextLength = TraceDisplay.maxScriptTraceChars,
            maximumBinaryBytes = TraceDisplay.maxScriptTraceChars,
            maximumDurationMillis = 100))
        return when (snapshot) {
            is SnapshotResult.Complete -> ExecutionValue.of(TraceDisplay.truncatedToString(
                snapshot.snapshot.value.get(), TraceDisplay.maxScriptTraceChars))
            SnapshotResult.Redacted -> ExecutionValue.of("<redacted>")
            is SnapshotResult.Rejected -> ExecutionValue.of(
                "<preview unavailable: ${snapshot.problems.firstOrNull()?.message ?: "rejected"}>")
        }
    }


    private fun displayTextOf(value: Any?): ExecutionValue = ExecutionValue.of(
        TraceDisplay.truncatedToString(value, TraceDisplay.maxScriptTraceChars))


    // The trace label a container passed-through by a control signal shows (decision 17) — a clearly-non-value
    // marker, not real step data. The client (phase 5) may refine the wording.
    private fun signalDisplay(signal: ScriptControlSignal): String {
        return when (signal) {
            is ScriptControlSignal.SkipIteration -> "→ skip iteration"
            is ScriptControlSignal.FinishLoop -> "→ finish loop"
            ScriptControlSignal.EndScript -> "→ end script"
        }
    }


}
