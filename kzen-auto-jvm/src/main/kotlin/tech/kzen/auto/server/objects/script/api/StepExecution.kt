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
 * The per-run, per-Script context a [ScriptStep] executes against — the engine-agnostic successor to the old
 * ScriptExecutionContext. A step reads its in-scope values, settles boundaries, runs nested branches, hosts a
 * child Logic, and (for a Result step) sets the Script result THROUGH this façade; everything that varies per
 * run lives here, while a step's static configuration and services come from its own constructor.
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


    //-----------------------------------------------------------------------------------------------------------------
    /** The value a referenced in-scope object (predecessor step / parameter / enclosing loop item) currently holds. */
    fun referencedValue(location: ObjectLocation): Any?

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


    //------------------------------------------------------------------------------------------ StepExecution: resources
    /**
     * Open a run-scoped resource (e.g. a browser) under [key]: store [value] for later [resource] reads by any
     * step of this run — and any Script it hosts — and register its disposal with the engine per [closePolicy]
     * so it is torn down when the owning run settles. Re-opening the same key replaces the prior handle + closer.
     */
    fun openResource(key: String, value: Any?, closePolicy: ResourceClosePolicy, closer: () -> Unit)

    /** The live handle a prior [openResource] stored under [key], or null if none is open. */
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
     * Drop the given steps — and everything nested within them — from the live-edit replay set, so they execute
     * live rather than re-adopting a stale outcome. A loop that did not complete pre-edit calls this on its body.
     */
    fun dropReplay(steps: List<ObjectLocation>)

    /**
     * Host a linked Logic document (another Script / Flow / Job) as a confined child node — the RunStep primitive.
     * The child is compiled on demand (cached for this run) and driven by the engine, so stepping crosses the
     * boundary uniformly.
     */
    suspend fun host(instructions: ObjectLocation, arguments: TupleValue): TupleValue
}
