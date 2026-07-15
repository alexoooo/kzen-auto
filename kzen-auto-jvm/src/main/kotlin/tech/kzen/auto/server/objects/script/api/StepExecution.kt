package tech.kzen.auto.server.objects.script.api

import tech.kzen.auto.common.objects.document.script.model.ScriptTree
import tech.kzen.auto.common.objects.document.script.model.ScriptValidation
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.logic.ResourceClosePolicy
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.GraphNotation


/**
 * The per-run, per-Script context a [ScriptStep] executes against — engine-agnostic. A step reads its in-scope
 * values, settles boundaries, runs nested branches, hosts a child Logic, and (for a Result step) sets the Script
 * result THROUGH this façade; everything that varies per run lives here, while a step's static configuration and
 * services come from its own constructor.
 *
 * This is what makes Script steps third-party-extensible: a step is an ordinary `@Reflect` notation object whose
 * [ScriptStep.run] the engine invokes polymorphically — exactly like a `FlowVertex` or a `Worker`, with no
 * central type dispatch. The framework spine ([runSteps]) owns the uniform per-step boundary / trace / replay
 * lifecycle; a step only computes its value (and, for a control step, chooses which nested branch to run).
 */
interface StepExecution {
    //-----------------------------------------------------------------------------------------------------------------
    // Per-Script structures a step needs to type / compile an expression (Formula / Result / DoWhile) or to
    // resolve its own nested objects (a loop's item binding, a RunStep's instructions link).
    val scriptTree: ScriptTree
    val scriptValidation: ScriptValidation
    val resultSignature: TupleDefinition
    val graphNotation: GraphNotation


    //-----------------------------------------------------------------------------------------------------------------
    /** Settle at a pausable boundary. The spine settles one before each step; a long-running step may settle more. */
    suspend fun checkpoint()

    /** Self-pause as an explicit breakpoint (the Pause step) — distinct from an ordinary boundary settle. */
    suspend fun pauseHere()

    /**
     * Return the value cached under [key] for this run, computing it once via [factory] on first request. The run
     * coroutine owns the cache single-threadedly, so the memoized value needs no locking — used to reuse a
     * compiled-expression instance across a loop's iterations, keyed by the expression's content signature.
     */
    fun <T: Any> perRunSingleton(key: String, factory: () -> T): T


    //-----------------------------------------------------------------------------------------------------------------
    /** The value a referenced in-scope object (predecessor step / parameter / enclosing loop item) currently holds. */
    fun referencedValue(location: ObjectLocation): Any?

    /**
     * Does anything in this Script read [location]'s value — an expression naming it, an attribute referencing it,
     * or the branch it terminates? A step that accumulates a value at some cost (a loop collecting every
     * iteration's output) can skip the accumulation when the answer is false, and still return a well-typed empty
     * result. Statically derived at compile time and deliberately conservative: an uncertain answer is always
     * `true`, so acting on `false` is safe (see [ScriptValueReferences][tech.kzen.auto.server.exec.script.ScriptValueReferences]).
     *
     * NB: do NOT infer this from [referencedValue] call sites — an expression step resolves EVERY in-scope value
     * whether or not its code names it, so at run time everything looks referenced.
     */
    fun isValueReferenced(location: ObjectLocation): Boolean

    /**
     * A named argument this Script invocation was called with — the run inputs a hosting
     * [RunStep][tech.kzen.auto.server.objects.script.step.control.RunStep] passed, resolved by component name.
     */
    fun argument(name: TupleComponentName): Any?

    /**
     * Record a value for downstream reference WITHOUT a step-trace entry — a binding (loop item) that is
     * resolved by reference but is not itself a step in the spine.
     */
    fun bind(location: ObjectLocation, value: Any?)

    /** Capture the Script's result (last Result step wins). */
    fun setResult(value: TupleValue)


    //------------------------------------------------------------------------------------ StepExecution: control flow
    /**
     * Raise a control-flow completion signal (continue / break / return — see [ScriptControlSignal]). MUST be a
     * step's TERMINAL action: no [checkpoint] / [pauseHere] / [host] / [runSteps] may follow in the same run, or a
     * park would strand a live signal. The spine short-circuits the remaining steps; a loop ([consumeLoopSignal])
     * or the Script root consumes it. Signals never survive a checkpoint, migrate, or cross a [host] boundary.
     */
    fun raiseControlSignal(signal: ScriptControlSignal)

    /**
     * For a loop step: return-and-clear a [ScriptControlSignal.SkipIteration] / [ScriptControlSignal.FinishLoop]
     * targeting [selfLocation] (stable-id compare), else null — leaving a signal targeting an OUTER loop or the
     * Script root ([ScriptControlSignal.EndScript]) pending for the enclosing frame to consume. Part of the loop
     * step contract (a `rerun`-flagged loop consumes its own Skip/Finish; see [ScriptStep.nestedStepLists]).
     */
    fun consumeLoopSignal(selfLocation: ObjectLocation): ScriptControlSignal?

    /** Peek the pending control signal without clearing it — a loop uses it to detect a foreign signal to propagate. */
    fun pendingControlSignal(): ScriptControlSignal?


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * Record opaque mid-flight migration sub-state for the step at [location] — the step-granularity parallel of
     * the engine's `onCapture`: it rides the Script's live-edit capture (keyed by the step's rename-stable id)
     * and is readable on the rebuilt run via [restoredCarry]. A loop records its iteration cursor here so a
     * pause -> edit -> resume continues at the current iteration instead of restarting; any step type — including
     * third-party — can carry its own state the same way. Pass null to CLEAR: a step that ran to completion must
     * clear its carry so a stale cursor never migrates (its completed outcome carries instead).
     */
    fun recordCarry(location: ObjectLocation, state: Any?)

    /**
     * The mid-flight state the step at [location] [recordCarry]'d in the pre-edit run, or null on a fresh run
     * (or when the step completed and cleared it). A restored carry survives ANY edit — the step decides how to
     * resume against the new definition (added nested steps run live, removed ones drop out — the logic-spec §5
     * element-level contract).
     */
    fun restoredCarry(location: ObjectLocation): Any?


    //-------------------------------------------------------------------------------------------- StepExecution: tracing
    /**
     * Record a trace detail for the currently-running step — a screenshot ([tech.kzen.lib.common.exec.BinaryExecutionValue],
     * also retained on the run's history film-strip) or a computed value — surfaced as the step's
     * [detail][tech.kzen.auto.common.objects.document.script.model.StepTrace.detail] and shown live while the step
     * is still running (it persists into the step's Done trace).
     */
    fun traceDetail(detail: ExecutionValue)

    /** [traceDetail] for an arbitrary value (a Boolean flag, a String) — coerced to an [ExecutionValue]. */
    fun traceDetail(detail: Any?) {
        traceDetail(ExecutionValue.ofArbitrary(detail) ?: ExecutionValue.of(detail.toString()))
    }

    /**
     * Record a short human-readable diagnostic for the currently-running step — e.g. which target crop
     * matched and where — surfaced as the step's
     * [note][tech.kzen.auto.common.objects.document.script.model.StepTrace.note] beside the detail
     * (it persists into the step's Done trace, so a run is diagnosable after the fact).
     */
    fun traceNote(note: String)


    //------------------------------------------------------------------------------------------ StepExecution: resources
    /**
     * Open a run-scoped resource (e.g. a browser) under [key]: register [value] and its disposal with the
     * engine per [closePolicy], so later [resource] reads see the handle from any step of this run — and any
     * document it hosts (Script, Flow, or Job — the engine reads along the host chain) — and it is torn down
     * when the owning document settles. The registration survives a live edit with its owning frame
     * (logic-spec §5 "open resources"). Re-opening the same key replaces the prior handle + closer.
     */
    fun openResource(key: String, value: Any?, closePolicy: ResourceClosePolicy, closer: () -> Unit)

    /** The live handle a prior [openResource] stored under [key] (here or in a hosting ancestor), or null if none is open. */
    fun resource(key: String): Any?

    /**
     * Dispose-and-forget the resource under [key] because an explicit closing step tore it down itself, so the
     * engine's auto-disposer won't fire a second time.
     */
    fun releaseResource(key: String)


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * Run a nested step sequence (a branch / loop body) inline on this same node, returning the last step's value.
     * The spine owns each step's boundary, trace lifecycle, pause-on-error and live-edit replay; a control step
     * only decides what to run.
     */
    suspend fun runSteps(steps: List<ObjectLocation>): Any?

    /**
     * Reset the given steps — and everything nested within them — for a fresh pass: drop them from the live-edit
     * replay set (so they execute live rather than re-adopting a stale outcome), drop their restored carries
     * (so a nested loop's cursor from a different enclosing iteration is never consumed), drop their entries
     * from the migration capture source (so a capture taken mid-iteration carries only the CURRENT iteration's
     * completed prefix — the invariant mid-loop resume replays against), and discard the engine's migration
     * captures of hosted-child invocations these steps launched (a RunStep's sub-document — a fresh invocation
     * must not adopt the pre-edit one's state; logic-spec §5 "invocation identity"). It also resets the steps'
     * emitted traces plus the retained trace values of the hosted invocations they launched, so each iteration
     * presents a fresh trace while the film-strip history survives (logic-spec §7 resettable live state).
     * A loop calls this at the start of every iteration except one it is resuming mid-flight, whose prefix
     * must stay replayable.
     */
    fun dropReplay(steps: List<ObjectLocation>)

    /**
     * Host a linked Logic document (another Script / Flow / Job) as a confined child node — the RunStep primitive.
     * The child is compiled on demand (cached for this run) and driven by the engine, so stepping crosses the
     * boundary uniformly.
     */
    suspend fun host(instructions: ObjectLocation, arguments: TupleValue): TupleValue
}
