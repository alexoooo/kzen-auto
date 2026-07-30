package tech.kzen.auto.server.exec.script

import tech.kzen.auto.common.objects.document.logic.context.ContextConventions
import tech.kzen.auto.common.objects.document.logic.context.ContextDescriptor
import tech.kzen.auto.common.objects.document.logic.context.LogicContextConventions
import tech.kzen.auto.common.objects.document.script.model.ScriptJumpAnalysis
import tech.kzen.auto.common.objects.document.script.model.ScriptTree
import tech.kzen.auto.common.objects.document.script.model.ScriptValidation
import tech.kzen.auto.common.objects.document.script.model.StepTrace
import tech.kzen.auto.common.util.TraceDisplay
import tech.kzen.auto.server.exec.LogicCompiler
import tech.kzen.auto.server.objects.script.api.ScriptControlSignal
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
 *
 * MOVE-TO (Set Next Statement, execution-control phase 2): a migration may carry a jump target
 * ([Execution.moveTarget]); [restore] then performs outcome-set surgery instead of a plain restore (drop the
 * target and everything at/after it — discarding the captures of any child invocations those dropped steps
 * hosted, which the re-run abandons — mark the pre-target skips value-less, run the descend ancestors with their
 * checkpoint suppressed), so the rebuilt paused spine re-runs from / skips to the target and parks there. The
 * surgery is computed by the notation-driven [ScriptJumpAnalysis]; the jump shares the migrate barrier, so an
 * edit-then-jump takes both in one rebuild.
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

    // Move-to (Set Next Statement) surgery, seeded by [restore] when the migration carried a jump target: steps
    // the rebuilt spine short-circuits with NO value ([skippedSteps] — forward-skipped over; a later reference
    // to one error-parks via [referencedValue]) and the jump target's ancestor containers, which the spine runs
    // (re-evaluating an If's condition) but does NOT park at ([descendSteps]), so the paused rebuild parks at the
    // target rather than the ancestor's boundary. Both empty on an ordinary run / edit-migrate.
    private val skippedSteps = HashSet<ObjectStableId>()
    private val descendSteps = HashSet<ObjectStableId>()

    // The trace detail a partially-committed step brought with its value ([ScriptStep.partialOutcome]) — the
    // journal a loop the jump skipped over had built while it was running. Consulted by [adoptOutcome], whose
    // re-emit would otherwise blank it. Empty on an ordinary run / edit-migrate.
    private val partialDetails = HashMap<ObjectStableId, ExecutionValue>()

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
    // way): the typed context API resolves the step's `provides` / `requires` / `releases` from NOTATION, and
    // the stable id alone cannot address it.
    private var currentStepLocation: ObjectLocation? = null

    // Per-step context declarations, read from notation once per location: the spine's requires gate consults
    // them before EVERY step, including a loop body's on every iteration. Confined to the run coroutine.
    private val declaredContextsCache = HashMap<ObjectLocation, List<ContextDescriptor>>()
    private val requiredContextsCache = HashMap<ObjectLocation, List<ContextDescriptor>>()


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
        return stepValues[stableId]
    }


    override fun isValueReferenced(location: ObjectLocation): Boolean {
        return location in structure.valueReferencedSteps
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
    // The typed layer over the raw resource API below: a step names a Context object (or, declaring exactly
    // one, names nothing) and this resolves it to the engine key. Ownership is the engine's — the furthest
    // document on the key's export chain, else the providing document (see [Execution.resource]); nothing here
    // reaches up on the opener's behalf.
    override fun declaredContexts(): List<ContextDescriptor> {
        val stepLocation = currentStepLocation
            ?: return listOf()
        return declaredContextsCache.getOrPut(stepLocation) {
            LogicContextConventions.stepDeclaredContexts(graphNotation, stepLocation)
        }
    }


    override fun provideContext(
        value: Any?,
        closePolicy: ResourceClosePolicy,
        qualifier: String?,
        closer: () -> Unit
    ) {
        val stepLocation = currentStepLocation
            ?: error("No step is running")
        val descriptor = LogicContextConventions.stepProvides(graphNotation, stepLocation)
            ?: error("Step declares no `provides` context: $stepLocation")
        execution.resource(resourceKeyOf(descriptor, qualifier), closePolicy.toEngine(), value, closer)
    }


    override fun contextValue(context: ObjectLocation?, qualifier: String?): Any {
        val descriptor = resolveDeclaredContext(context)
        return execution.resourceValue(resourceKeyOf(descriptor, qualifier))
            ?: error(missingContextMessage(descriptor, qualifier))
    }


    override fun contextValueOrNull(context: ObjectLocation?, qualifier: String?): Any? {
        val descriptor = resolveDeclaredContext(context)
        return execution.resourceValue(resourceKeyOf(descriptor, qualifier))
    }


    override fun releaseContext(context: ObjectLocation?, qualifier: String?) {
        val descriptor = resolveDeclaredContext(context)
        execution.releaseResource(resourceKeyOf(descriptor, qualifier))
    }


    // The Context an argument-free call means: the step's SOLE declaration across `provides` / `requires` /
    // `releases`. All three kinds count — a provider declares no `requires` yet still reads back on its
    // replace-existing path, and a closer declares only `releases` yet must resolve something.
    private fun resolveDeclaredContext(context: ObjectLocation?): ContextDescriptor {
        val declared = declaredContexts()

        if (context != null) {
            // Naming a Context explicitly does not require declaring it — a step may read one it did not
            // declare (at the cost of the spine's requires gate not covering it).
            return declared.firstOrNull { it.location == context }
                ?: ContextConventions.descriptorOrNull(graphNotation, context)
                ?: error("Not a context: $context")
        }

        return when {
            declared.isEmpty() ->
                error("Step declares no context — declare one as `provides` / `requires` / `releases`, " +
                        "or name the context to use")

            declared.size > 1 ->
                error("Step declares several contexts (${declared.joinToString { it.label() }}) — " +
                        "name the one to use")

            else ->
                declared.single()
        }
    }


    // A Context's engine key, family-qualified when a qualifier addresses one member (a SUT by name) —
    // matching the "<family>:<qualifier>" form the engine's export matching and family check both read.
    private fun resourceKeyOf(descriptor: ContextDescriptor, qualifier: String?): String {
        return when {
            qualifier.isNullOrEmpty() -> descriptor.key
            else -> "${descriptor.key}:$qualifier"
        }
    }


    private fun missingContextMessage(descriptor: ContextDescriptor, qualifier: String?): String {
        val label = descriptor.label()
        return when {
            qualifier.isNullOrEmpty() -> "Requires $label: not provided"
            else -> "Requires $label '$qualifier': not provided"
        }
    }


    //------------------------------------------------------------------------------------------ StepExecution: resources
    // Delegated wholly to the engine, which stores the live handle with the registration: reading walks the
    // ancestor chain (so a hosted child — Script, Flow, or Job — borrows the handle its host opened), and the
    // registration survives a live edit with its owning frame's stable identity (logic-spec §5/§6).
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
        var last: Any? = null
        for (stepLocation in steps) {
            val stableId = objectStableMapper.objectStableId(stepLocation)

            // Move-to (Set Next Statement) forward-skip: the rebuilt spine walked past this step to reach a later
            // target, so it produces NO value (a later reference to it error-parks via [referencedValue]) — no
            // checkpoint, no outcome. `last` is left untouched (a skipped step contributes nothing).
            if (stableId in skippedSteps) {
                emitStepTrace(stableId, StepTrace.State.Skipped, NullExecutionValue)
                continue
            }

            // Live-edit replay (logic-spec §5): a step that completed in the pre-edit run re-adopts its outcome
            // without re-executing — no "next to run" highlight, no checkpoint boundary, no work.
            if (restoredOutcomes.containsKey(stableId)) {
                last = adoptCompleted(stepLocation, stableId)
                continue
            }

            val step = scriptStepAt(stepLocation)

            // Move-to descend: an ancestor of the jump target runs (an If re-evaluates its condition) but its
            // checkpoint is suppressed (claim-once), so the paused rebuild parks at the target inside its branch,
            // not at the ancestor's own boundary. Ordinary steps always take the boundary.
            val suppressBoundary = descendSteps.remove(stableId)
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
                last = execution.recoverable({ error ->
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
                    checkRequiredContexts(stepLocation)
                    step.run(this)
                }

                // Control flow (continue/break/return — see [ScriptControlSignal]): after the step runs, a pending
                // signal short-circuits the walk. A CONTAINER the signal merely passed through (an If, or a loop
                // propagating an outer signal) gets a Done trace NAMING the signal but no completedOutcomes entry
                // (a container's value is never referenced from outside its scope, so skipping markDone is safe);
                // the step that RAISED the signal gets its normal Done. Either way the remaining steps do not run —
                // a loop ([consumeLoopSignal]) or the root ([consumeRootSignalOrFail]) consumes the signal.
                val signal = pendingSignal
                if (signal != null && pendingSignalRaisedBy != stableId) {
                    emitStepTrace(stableId, StepTrace.State.Done, displayOf(signalDisplay(signal)), currentDetail)
                    return last
                }
                markDone(stableId, last)
                if (signal != null) {
                    return last
                }
            }
            finally {
                currentStableId = previousStableId
                currentStepLocation = previousStepLocation
                currentDetail = previousDetail
                currentNote = previousNote
            }
        }
        return last
    }


    /**
     * The uniform requires gate: a step whose declared `requires` are not open fails HERE rather than at
     * whatever ad-hoc read it happens to reach. Called inside the [Execution.recoverable] unit, so the
     * failure gets the standard framing for free — an Error trace, a pause-on-error park, and a re-check when
     * the run resumes (so providing the context and resuming lets the step proceed).
     *
     * FAMILY-granular by design (see [Execution.hasResourceInFamily]): a qualifier is a step parameter and may
     * be computed, so this answers "is SOME SUT started", never "is `sut:other` started". A qualifier mismatch
     * therefore passes the gate and surfaces at read, with the step's own diagnostic. Steps declaring
     * `releases` are deliberately NOT gated: a closer's job is to make the absence true.
     */
    private fun checkRequiredContexts(stepLocation: ObjectLocation) {
        val required = requiredContextsCache.getOrPut(stepLocation) {
            LogicContextConventions.stepRequires(graphNotation, stepLocation)
        }
        for (descriptor in required) {
            if (! execution.hasResourceInFamily(descriptor.key)) {
                error("Requires ${descriptor.label()}: not provided")
            }
        }
    }


    // The generic iteration reset (see the [StepExecution.dropReplay] contract): beyond the replay set, also
    // prunes the capture source — so a mid-iteration capture carries only the current iteration's completed
    // prefix — and the restored carries, so a nested loop's cursor from a different enclosing iteration is
    // never consumed by a later fresh pass. [stepValues] (the live value graph) is deliberately untouched.
    // The engine-side discard tells the run that hosted-child invocations launched from these steps (a
    // RunStep in the loop body) are abandoned — a fresh invocation must not adopt the pre-edit one's
    // migration capture (logic-spec §5 "invocation identity"). The engine-side reset is the OBSERVABLE half:
    // the steps' emitted traces (the addresses mirror [emitStepTrace]) and the retained trace values of the
    // hosted invocations their RunSteps launched clear, so the fresh pass presents a fresh trace while the
    // film-strip history survives (logic-spec §7 resettable live state).
    override fun dropReplay(steps: List<ObjectLocation>) {
        val stableIds = nestedStableIds(steps)
        for (stableId in stableIds) {
            restoredOutcomes.remove(stableId)
            restoredCarries.remove(stableId)
            completedOutcomes.remove(stableId)
        }
        execution.discardCaptured(stableIds)
        execution.resetEmitted(stableIds.map { Address.of(it.value) }, stableIds)
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
    /**
     * Record a parameter / binding value by stable id in the live value graph, with NO trace emit (see the
     * [bind][tech.kzen.auto.server.objects.script.api.StepExecution.bind] contract — a loop-item binding is
     * re-bound every iteration and nothing reads its address). A parameter's display value is surfaced
     * separately, once at run start, by [ScriptLogic] via [tech.kzen.auto.server.exec.LogicParameterTrace].
     */
    fun recordValue(stableId: ObjectStableId, value: Any?) {
        stepValues[stableId] = value
    }


    fun result(): TupleValue? {
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
     * Seed the carried-over completed work from the predecessor run's capture (read once at run start).
     *
     * Work an element the edit DELETED produced is dropped rather than carried ([removedStableIds], logic-spec
     * §5): a stable id is the element's address, so a step created where the deleted one stood mints the same
     * id and would otherwise be replay-adopted as Done — reported complete, holding the deleted step's value,
     * having never run. The same filter drops an id that resolves to nothing at all, which is that removal
     * seen from a run whose barrier could not report it (a deleted document).
     *
     * When the migration barrier carried a move-to [moveTarget] (Set Next Statement) that resolves to a valid
     * jump in the current [ScriptTree] ([jumpPlanFor]), apply outcome-set surgery instead of a plain restore:
     * drop the target and everything at/after it in document order, plus the descend ancestors (an enclosing
     * If) — so the rebuilt paused spine re-runs from (backward) or skips to (forward) the target and parks there.
     * Steps the walk visits before the target but keeps no outcome for become [skippedSteps] (value-less),
     * UNLESS one was mid-flight and offers a partial value ([ScriptStep.partialOutcome] — a loop's collected
     * iterations), which is committed as a restored outcome instead; the ancestors become [descendSteps] (run,
     * checkpoint suppressed). A dropped step's hosted child invocations are additionally discarded
     * ([Execution.discardCaptured]) — the step re-runs, so its pre-jump sub-execution is abandoned and must not
     * be adopted by the fresh one. An unsupported / unresolvable [moveTarget]
     * falls back to a full restore — the engine ignore-contract (the controller's `canMoveTo` gate normally
     * makes that unreachable). The carried [result] is kept (decision 11); a Result at/after the target re-runs.
     */
    fun restore(
        state: ScriptMigrationState,
        moveTarget: ObjectStableId?,
        removedStableIds: Set<ObjectStableId>
    ) {
        val carriedOutcomes = state.completedOutcomes.filterKeys { survivesEdit(it, removedStableIds) }
        val carriedCarries = state.stepCarry.filterKeys { survivesEdit(it, removedStableIds) }

        val plan = moveTarget?.let { jumpPlanFor(it) }
        if (plan == null) {
            restoredOutcomes.putAll(carriedOutcomes)
            restoredCarries.putAll(carriedCarries)
            resultValue = state.result
            return
        }

        val documentPath = structure.scriptLocation.documentPath
        val dropStableIds = plan.dropSet.mapTo(HashSet()) {
            objectStableMapper.objectStableId(ObjectLocation(documentPath, it))
        }

        // A dropped step RE-RUNS, so any child invocation it hosted (a RunStep's sub-Script) is abandoned: its
        // capture must not be adopted by the fresh invocation the re-run launches. This is the same
        // invocation-identity signal a re-running loop sends via [dropReplay] (logic-spec §5) — and it is
        // required here for the same reason: [Execution.restored] is delivered on (stableId, callSite), both of
        // which a re-hosted child still matches, so without the discard the sub-Script adopts its pre-jump
        // outcomes, replay-short-circuits every step, and appears to re-run instantaneously while handing back
        // its stale values. Must precede the hosting, which it does — [restore] runs at [ScriptLogic.run] start.
        //
        // Deliberately scoped to the DROP set, not the skip set: a step the walk skips over never runs in the
        // rebuilt spine, so it never re-hosts and nothing can adopt its child's capture — that capture is simply
        // unclaimed, and the engine's orphan sweep disposes it at the next barrier like any other (see
        // [RunEngine.sweepOrphans]: "an orphaned detached resource lingers at most one edit cycle").
        execution.discardCaptured(dropStableIds)

        for ((stableId, value) in carriedOutcomes) {
            if (stableId !in dropStableIds) {
                restoredOutcomes[stableId] = value
            }
        }
        for ((stableId, carry) in carriedCarries) {
            // A dropped loop restarts at iteration 0, so its stale cursor must not carry (else it would resume
            // mid-iteration instead of restarting).
            if (stableId !in dropStableIds) {
                restoredCarries[stableId] = carry
            }
        }
        resultValue = state.result

        for (ancestor in plan.ancestors) {
            descendSteps.add(objectStableMapper.objectStableId(ObjectLocation(documentPath, ancestor)))
        }
        for (preceding in plan.precedingOnPath) {
            val location = ObjectLocation(documentPath, preceding)
            val stableId = objectStableMapper.objectStableId(location)
            if (stableId in restoredOutcomes) {
                continue
            }

            // A step the jump walked past that was MID-FLIGHT may still have a value worth handing downstream
            // (a loop's collected iterations — see [ScriptStep.partialOutcome]). Committing it as a restored
            // outcome routes it through the spine's existing replay short-circuit: adopted, never re-run, Done,
            // and present in [stepValues] so a later reference resolves instead of error-parking.
            val partial = restoredCarries[stableId]?.let { scriptStepAt(location).partialOutcome(it) }
            if (partial == null) {
                skippedSteps.add(stableId)
            }
            else {
                restoredOutcomes[stableId] = partial.value
                partialDetails[stableId] = partial.detail

                // It is adopted rather than re-run, so its cursor is spent — a stale one must not survive to be
                // consumed by a later backward jump back into the loop.
                restoredCarries.remove(stableId)
            }
        }

        // Reset the dropped steps' stale displays: the rebuilt spine parks at the target and never re-walks to
        // them, so their old Done / Error traces would otherwise linger. (Skipped steps get their Skipped trace
        // from the spine when the walk reaches them; the target repaints Running at its checkpoint.)
        for (stableId in dropStableIds) {
            emitStepTrace(stableId, StepTrace.State.Idle, NullExecutionValue)
        }
    }


    // Whether the element that produced a carried entry is still the element this id names. The removal set is
    // the authority; the unresolvable-id check is the backstop for a removal no barrier reported.
    private fun survivesEdit(stableId: ObjectStableId, removedStableIds: Set<ObjectStableId>): Boolean {
        return stableId !in removedStableIds &&
                objectStableMapper.objectLocationOrNull(stableId) != null
    }


    // Resolve a move-to target stable id against the CURRENT structure to a valid [ScriptJumpAnalysis.ScriptJumpPlan],
    // or null when it does not resolve to a jumpable step in this Script's root document (the ignore-contract).
    private fun jumpPlanFor(moveTarget: ObjectStableId): ScriptJumpAnalysis.ScriptJumpPlan? {
        val targetLocation = objectStableMapper.objectLocationOrNull(moveTarget)
            ?: return null
        if (targetLocation.documentPath != structure.scriptLocation.documentPath) {
            return null
        }
        return ScriptJumpAnalysis
            .plan(structure.graphNotation, targetLocation.documentPath, structure.scriptTree, targetLocation.objectPath)
            .takeIf { it.valid }
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
            for (nestedList in scriptStepAt(location).nestedStepLists(structure.graphNotation)) {
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
    private fun adoptCompleted(stepLocation: ObjectLocation, stableId: ObjectStableId): Any? {
        for (nestedStepList in scriptStepAt(stepLocation).nestedStepLists(structure.graphNotation)) {
            for (nestedLocation in nestedStepList) {
                val nestedStableId = objectStableMapper.objectStableId(nestedLocation)
                if (restoredOutcomes.containsKey(nestedStableId)) {
                    adoptCompleted(nestedLocation, nestedStableId)
                }
            }
        }
        return adoptOutcome(stableId)
    }


    private fun adoptOutcome(stableId: ObjectStableId): Any? {
        val value = restoredOutcomes[stableId]
        stepValues[stableId] = value
        completedOutcomes[stableId] = value
        emitStepTrace(
            stableId, StepTrace.State.Done, displayOf(value),
            partialDetails[stableId] ?: NullExecutionValue)
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
    private fun displayOf(value: Any?): ExecutionValue {
        return ExecutionValue.of(
            TraceDisplay.truncatedToString(value, TraceDisplay.maxScriptTraceChars))
    }


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
