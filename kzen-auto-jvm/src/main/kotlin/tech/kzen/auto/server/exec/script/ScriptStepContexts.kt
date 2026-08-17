package tech.kzen.auto.server.exec.script

import tech.kzen.auto.common.objects.document.logic.context.ContextAddressing
import tech.kzen.auto.common.objects.document.logic.context.ContextCallBinding
import tech.kzen.auto.common.objects.document.logic.context.ContextConventions
import tech.kzen.auto.common.objects.document.logic.context.ContextDescriptor
import tech.kzen.auto.common.objects.document.logic.context.LogicContextConventions
import tech.kzen.auto.server.exec.ContextCallSite
import tech.kzen.lib.common.exec.engine.Execution
import tech.kzen.lib.common.exec.engine.context.BindingLookup
import tech.kzen.lib.common.exec.engine.context.ContextKey
import tech.kzen.lib.common.exec.engine.context.InitialBinding
import tech.kzen.lib.common.exec.engine.disposal.FrameDisposal
import tech.kzen.lib.common.exec.logic.ResourceClosePolicy
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.GraphNotation


/**
 * The typed context layer over the engine's raw resource API: a step names a Context object (or, declaring
 * exactly one, names nothing) and this resolves it — from the step's own notation declarations
 * (`binds` / `uses` / `releases` / `contexts`, cached per location) — to the engine key. Ownership is the
 * engine's — the furthest document on the key's export chain, else the binding document (see
 * [Execution.bind]); nothing here reaches up on the opener's behalf.
 *
 * Owned by a single [ScriptRunContext] and confined like it to the run coroutine, so the caches need no
 * locking; [currentStepLocation] supplies the step the spine is currently running, whose declarations
 * every argument-free resolution is scoped to.
 */
class ScriptStepContexts(
    private val execution: Execution,
    private val graphNotation: GraphNotation,
    private val currentStepLocation: () -> ObjectLocation?
) {
    //-----------------------------------------------------------------------------------------------------------------
    // Per-step context declarations, read from notation once per location: the spine's uses gate consults
    // them before EVERY step, including a loop body's on every iteration.
    private val declaredContextsCache = HashMap<ObjectLocation, List<ContextDescriptor>>()
    private val usedContextsCache = HashMap<ObjectLocation, List<ContextDescriptor>>()
    private val callContextsCache = HashMap<ObjectLocation, List<ContextCallBinding>>()


    //-----------------------------------------------------------------------------------------------------------------
    fun declaredContexts(): List<ContextDescriptor> {
        val stepLocation = currentStepLocation()
            ?: return listOf()
        return declaredContextsCache.getOrPut(stepLocation) {
            LogicContextConventions.stepDeclaredContexts(graphNotation, stepLocation)
        }
    }


    fun bindContext(value: Any?, qualifier: String?) {
        // Disposal is genuinely absent, not defaulted to a no-op closer: a null disposal is what makes the
        // engine's settle skip this binding entirely, and what makes a later release degenerate to unbinding.
        bindDeclared(value, qualifier, disposal = null)
    }


    fun bindContext(
        value: Any?,
        closePolicy: ResourceClosePolicy,
        qualifier: String?,
        closer: () -> Unit
    ) {
        bindDeclared(value, qualifier, FrameDisposal(closePolicy.toEngine(), closer))
    }


    // Both binds resolve, conform and address identically — they differ only in whether a disposal rides along,
    // which is exactly the ContextBinder / ResourceOwner split expressed at the runtime boundary.
    private fun bindDeclared(value: Any?, qualifier: String?, disposal: FrameDisposal?) {
        val stepLocation = currentStepLocation()
            ?: error("No step is running")
        val descriptor = LogicContextConventions.stepBinds(graphNotation, stepLocation)
            ?: error("Step declares no `binds` context: $stepLocation")
        checkBindConformance(descriptor, value)
        execution.bind(resourceKeyOf(descriptor, qualifier), value, disposal)
    }


    fun contextValue(context: ObjectLocation?, qualifier: String?): Any {
        val descriptor = resolveDeclaredContext(context)
        val lookup = execution.binding(resourceKeyOf(descriptor, qualifier))
        return when (lookup) {
            BindingLookup.Missing -> error(missingContextMessage(descriptor, qualifier))
            is BindingLookup.Present -> lookup.value
                ?: error(missingContextMessage(descriptor, qualifier))
        }
    }


    fun contextValueOrNull(context: ObjectLocation?, qualifier: String?): Any? {
        val descriptor = resolveDeclaredContext(context)
        return execution.binding(resourceKeyOf(descriptor, qualifier)).valueOrNull()
    }


    fun releaseContext(context: ObjectLocation?, qualifier: String?) {
        val descriptor = resolveDeclaredContext(context)
        // Removes the binding AND runs the disposal its binder attached, at most once — so a closing step
        // names what it releases and the engine performs the teardown, rather than each closer duplicating
        // the binder's knowledge of how its own handle dies. Tolerant of nothing being bound: releasing an
        // absent Context is a no-op, which is what makes a closer's "already gone is success" contract hold.
        execution.releaseBinding(resourceKeyOf(descriptor, qualifier))
    }


    /**
     * The uniform uses gate: a step whose declared `uses` are not bound fails HERE rather than at whatever
     * ad-hoc read it happens to reach. Called inside the [Execution.recoverable] unit, so the failure gets the
     * standard framing for free — an Error trace, a pause-on-error park, and a re-check when the run resumes
     * (so binding the context and resuming lets the step proceed).
     *
     * Granularity follows the DECLARATION: a declared qualifier is a static fact, so the gate asks the exact
     * question; only an unqualified declaration — the one that admits a computed qualifier — falls back to
     * "is SOME SUT started", because a qualifier computed at run time is unknowable here. A computed-qualifier
     * mismatch therefore still passes the gate and surfaces at read, with the step's own diagnostic. Steps
     * declaring `releases` are deliberately NOT gated: a closer's job is to make the absence true, so an
     * already-absent Context is the outcome it was asked for rather than a precondition it failed.
     */
    fun checkUsedContexts(stepLocation: ObjectLocation) {
        val used = usedContextsCache.getOrPut(stepLocation) {
            LogicContextConventions.stepUses(graphNotation, stepLocation)
        }
        for (descriptor in used) {
            val key = ContextAddressing.keyOf(descriptor)
            val open =
                if (key.qualifier != null) {
                    // A DECLARED qualifier is a static fact, so the gate can answer the exact question.
                    execution.hasBinding(key)
                }
                else {
                    execution.hasBindingInFamily(key.family)
                }

            if (!open) {
                error("Uses ${descriptor.label()}: not bound")
            }
        }
    }


    /**
     * The hosting step's `contexts:` map, resolved against the scope as it stands right now — what the caller
     * supplies to the child for this call and no longer.
     *
     * Read from the CURRENT step's notation rather than passed in by [tech.kzen.auto.server.objects.script.step.control.RunStep],
     * the same way [bindDeclared] reads its `binds`: a context declaration is notation the running step
     * carries, and routing it through the [tech.kzen.auto.server.objects.script.api.StepExecution] signature
     * would make every hosting step re-plumb what the runtime can already see. It also means a live edit needs
     * nothing — the rebuilt caller re-runs this and re-supplies from whatever its sources hold NOW, which is
     * precisely the contract [Execution.host] documents for a bootstrap value.
     *
     * **Every failure here is attributed to the hosting step**, which is the point of resolving before the
     * child exists: a source nothing bound is a mistake in the CALL, and surfacing it inside the callee — as
     * "uses X: not bound", several steps into a document that is not even the one at fault — is exactly the
     * misattribution the map is supposed to remove.
     */
    fun callSiteBindings(): List<InitialBinding> {
        val stepLocation = currentStepLocation()
            ?: return listOf()

        val callBindings = callContextsCache.getOrPut(stepLocation) {
            LogicContextConventions.stepCallContexts(graphNotation, stepLocation)
        }

        return ContextCallSite.initialBindings(execution, callBindings)
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Delegated to [ContextCallSite] so the Script spine and a Flow's logic-host vertex cannot drift on what a
    // bind admits: both resolve the same declarations against the same engine surface, and the check is the
    // definitive one on either path.
    private fun checkBindConformance(descriptor: ContextDescriptor, value: Any?) {
        ContextCallSite.checkBindConformance(descriptor, value)
    }


    // The Context an argument-free call means: the step's SOLE declaration across `binds` / `uses` /
    // `releases`. All three kinds count — a binder declares no `uses` yet still reads back on its
    // replace-existing path, and a closer declares only `releases` yet must resolve something.
    private fun resolveDeclaredContext(context: ObjectLocation?): ContextDescriptor {
        val declared = declaredContexts()

        if (context != null) {
            // Naming a Context explicitly does not require declaring it — a step may read one it did not
            // declare (at the cost of the spine's uses gate not covering it).
            return declared.firstOrNull { it.location == context }
                ?: ContextConventions.descriptorOrNull(graphNotation, context)
                ?: error("Not a context: $context")
        }

        return when {
            declared.isEmpty() ->
                error("Step declares no context — declare one as `binds` / `uses` / `releases`, " +
                        "or name the context to use")

            declared.size > 1 ->
                error("Step declares several contexts (${declared.joinToString { it.label() }}) — " +
                        "name the one to use")

            else ->
                declared.single()
        }
    }


    // A Context's engine address: its declared family (an explicit `key:` alias, else the canonical rendering
    // of its whole `type:`) plus a qualifier naming one member. A DECLARED qualifier resolves exactly and
    // combining it with a computed one is refused — see [ContextAddressing.keyOf].
    private fun resourceKeyOf(descriptor: ContextDescriptor, qualifier: String?): ContextKey {
        return ContextAddressing.keyOf(descriptor, qualifier)
    }


    // Deliberately the same framing the spine's uses gate produces ([checkUsedContexts]), so a typed read that
    // outruns its binder and a declaration the gate caught read alike.
    private fun missingContextMessage(descriptor: ContextDescriptor, qualifier: String?): String {
        val label = descriptor.label()
        return when {
            qualifier.isNullOrEmpty() -> "Uses $label: not bound"
            else -> "Uses $label '$qualifier': not bound"
        }
    }
}
