package tech.kzen.auto.server.objects.script.api

import tech.kzen.auto.common.objects.document.logic.context.ContextDescriptor
import tech.kzen.auto.common.objects.document.script.model.ScriptTree
import tech.kzen.auto.common.objects.document.script.model.ScriptValidation
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.engine.disposal.SettleDisposalPolicy
import tech.kzen.lib.common.exec.logic.ResourceClosePolicy
import tech.kzen.lib.common.exec.data.binding.BindingName
import tech.kzen.lib.common.exec.data.binding.BindingSchema
import tech.kzen.lib.common.exec.data.binding.DataBindings
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
    val resultSignature: BindingSchema
    val graphNotation: GraphNotation


    //-----------------------------------------------------------------------------------------------------------------
    /** Settle at a pausable boundary. The spine settles one before each step; a long-running step may settle more. */
    suspend fun checkpoint()

    /** Self-pause as an explicit breakpoint (the Pause step) — distinct from an ordinary boundary settle. */
    suspend fun pauseHere()

    /**
     * Run a BLOCKING [block] off the engine's fixed dispatcher pool (a Selenium round-trip, a large file read)
     * so it doesn't hold an engine thread — see [Execution.blocking][tech.kzen.lib.common.exec.engine.Execution.blocking].
     * The block must be interrupt-responsive; engine cancel / migrate reach it by interrupting its worker thread.
     */
    suspend fun <R> blocking(block: () -> R): R

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
    fun argument(name: BindingName): Any?

    /**
     * Record a value for downstream reference WITHOUT a step-trace entry — a binding (loop item) that is
     * resolved by reference but is not itself a step in the spine.
     *
     * Named for what it does to the VALUE GRAPH, not for the ambient scope: the notation verb `binds:` and the
     * engine's `bind` both name putting a value in ambient scope under a Context, which is a different
     * operation on a type this same object implements.
     */
    fun recordValue(location: ObjectLocation, value: Any?)

    /** Capture the Script's result; the Result step then raises [ScriptControlSignal.EndScript]. */
    fun setResult(value: DataBindings)


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


    //------------------------------------------------------------------------------------------- StepExecution: context
    /**
     * The Contexts the currently-running step DECLARES, in any role — its `binds`, its `uses`, and its
     * `releases`, deduplicated. This is what the argument-free forms below resolve against: a step declaring
     * exactly one Context names nothing; a step declaring several passes the one it means.
     */
    fun declaredContexts(): List<ContextDescriptor>

    /**
     * Publish [value] into the run's ambient scope under this step's `binds:` Context, owning nothing: the
     * binding carries **no disposal**, so nothing is ever torn down on its account. This is the form for an
     * ambient value — a String, a config object, a handle someone else owns — and it is the reason binding and
     * owning are separate mix-ins (`ContextBinder` versus `ResourceOwner`): saying what a name means says
     * nothing about who ends it. A later [releaseContext] simply removes the name.
     *
     * Ownership-climbing, supersession and visibility are exactly as the managed overload below describes;
     * only the teardown is absent. [qualifier] addresses one member of a Context family.
     */
    fun bindContext(value: Any?, qualifier: String? = null)

    /**
     * Publish [value] under this step's `binds:` Context **and** attach the disposal that ends it, per
     * [closePolicy] — the managed form, for a resource this step opened and therefore owns. Ownership climbs
     * the *export chain*: past this document if it declares that Context in `context.exports`, onward while
     * each caller in turn exports it too, resting at the first that does not. A Context this document does not
     * export is PRIVATE to it, disposed at its own settle — so a sub-script may open a browser the root owns,
     * but only because the sub-script offered it up. [qualifier] addresses one member of a Context family
     * (a SUT by name).
     *
     * Later [contextValue] reads see the handle from any step of this run and from any document it hosts
     * (Script, Flow, or Job — the engine reads along the host chain). The registration survives a live edit
     * with its owning frame (logic-spec §5 "open resources").
     *
     * Re-binding the same Context + [qualifier] **supersedes**: the displaced registration's closer runs, so
     * a step that re-opens in a loop does not leak. [closer] must therefore dispose the handle it CAPTURED and
     * never re-resolve its target by name — it runs after the replacement is already registered (the closer
     * contract on [tech.kzen.lib.common.exec.engine.Execution.bind]). A step that deliberately replaces an
     * existing resource should instead [releaseContext] it first, so the old handle is torn down while it is
     * still what the name resolves to rather than after the replacement has taken its place.
     */
    fun bindContext(
        value: Any?,
        closePolicy: ResourceClosePolicy,
        qualifier: String? = null,
        closer: () -> Unit)

    /**
     * Register frame cleanup with NO value and NO name: run [closer] when the document that owns this step
     * settles, per [policy]. The disposal half of [bindContext] without the binding half — "delete this file",
     * "kill this helper" — for work that has nothing anyone would want to read, and therefore needs no Context
     * invented to give it a `finally`.
     *
     * [policy] has two values, not the three a managed binding has, and the missing one is not an oversight:
     * `manual` is a *promotion* — it hands a registration one frame up so a later step can still find it and
     * close it — and an anonymous registration has no name for anything to find it by
     * (see [tech.kzen.lib.common.exec.engine.disposal.SettleDisposalPolicy]).
     *
     * [closer] runs on the calling thread at settle, so anything genuinely blocking belongs behind the same
     * [blocking] treatment a step body would give it.
     */
    fun disposeAtSettle(policy: SettleDisposalPolicy, closer: () -> Unit)

    /**
     * The live handle held for [context] (defaulting to this step's sole declared Context), failing uniformly
     * when nothing is open — the same framing the spine's uses gate produces, so a typed read and a
     * missing declaration read alike.
     */
    fun contextValue(context: ObjectLocation? = null, qualifier: String? = null): Any

    /** [contextValue] without the failure: null when nothing is open. What a step with its own diagnostic uses. */
    fun contextValueOrNull(context: ObjectLocation? = null, qualifier: String? = null): Any?

    /**
     * Release the resource held for [context]: the binding goes, and the disposal the step that opened it
     * attached runs, at most once. That split is what a closing step is FOR — it names what ends, while how
     * the handle dies stays with the [bindContext] that knows, so no closer duplicates it and nothing is
     * torn down twice. Tolerant: releasing what is not open is a no-op. A binding made by the disposal-free
     * [bindContext] has nothing attached, so releasing it degenerates safely to removing the name.
     */
    fun releaseContext(context: ObjectLocation? = null, qualifier: String? = null)


    //------------------------------------------------------------------------------------------ StepExecution: resources
    // The raw string-keyed layer beneath the typed Context API above, mirroring kzen-lib's own supported raw
    // interop surface. Keys are a global namespace, so a raw caller and a typed one that name the same key
    // share one registration — which is the point: a plugin (or a test fixture) can interoperate with a
    // first-party Context without declaring one. What a step gives up by coming here is DECLARATION, not
    // correctness — nothing raw is visible to the static analysis, so a raw open leaves a downstream typed
    // `uses` unsatisfiable. Reach for it only when the key genuinely is not known at authoring time.
    /**
     * Open a run-scoped resource (e.g. a browser) under [key]: register [value] and its disposal with the
     * engine per [closePolicy], so later [resource] reads see the handle from any step of this run — and any
     * document it hosts (Script, Flow, or Job — the engine reads along the host chain) — and it is torn down
     * when the owning document settles. The registration survives a live edit with its owning frame
     * (logic-spec §5 "open resources"). Re-opening the same key **supersedes**: the displaced registration's
     * closer runs, so [closer] must dispose the handle it CAPTURED rather than re-resolving its target by
     * name — see [bindContext] and the closer contract on
     * [tech.kzen.lib.common.exec.engine.Execution.resource].
     */
    fun openResource(key: String, value: Any?, closePolicy: ResourceClosePolicy, closer: () -> Unit)

    /** The live handle a prior [openResource] stored under [key] (here or in a hosting ancestor), or null if none is open. */
    fun resource(key: String): Any?

    /**
     * Dispose-and-forget the resource under [key] because an explicit closing step tore it down itself, so the
     * engine's auto-disposer won't fire a second time. Not the raw spelling of [releaseContext], which removes
     * the name AND runs the teardown attached to it — these are two different operations.
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
    suspend fun host(instructions: ObjectLocation, arguments: DataBindings): DataBindings
}


/**
 * The unique Context this step declares whose value contract names [T]. That is what disambiguates a step
 * declaring several — `BrowserGetSutStep` requires both a browser and a SUT, and each is addressed by its
 * value type rather than by name. Matched on the declared class exactly, so a subtype does not resolve here.
 *
 * Fails when no declared Context names [T], or when several do (name the Context explicitly then).
 */
inline fun <reified T: Any> StepExecution.contextDescriptor(): ContextDescriptor {
    val className = T::class.qualifiedName
    val matching = declaredContexts().filter { it.type.className.asString() == className }

    return when {
        matching.isEmpty() ->
            error("No declared context of type $className — declare one as `binds` / `uses` / `releases`")

        matching.size > 1 ->
            error("Several declared contexts of type $className " +
                    "(${matching.joinToString { it.label() }}) — name the one to read")

        else ->
            matching.single()
    }
}


/**
 * The typed read: the live handle of this step's declared [T]-valued Context, failing uniformly when nothing
 * is open for it. The ordinary form for a step that declares the Context as a `uses` — the spine's gate
 * already covered the family, so this only fails on a qualifier the gate cannot see.
 */
inline fun <reified T: Any> StepExecution.context(qualifier: String? = null): T {
    return contextValue(contextDescriptor<T>().location, qualifier) as T
}


/** [context] without the failure: null when nothing is open. For a closer, or a step with its own diagnostic. */
inline fun <reified T: Any> StepExecution.contextOrNull(qualifier: String? = null): T? {
    return contextValueOrNull(contextDescriptor<T>().location, qualifier) as T?
}
