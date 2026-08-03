package tech.kzen.auto.server.exec

import tech.kzen.auto.common.objects.document.logic.context.ContextAddressing
import tech.kzen.auto.common.objects.document.logic.context.ContextCallBinding
import tech.kzen.auto.common.objects.document.logic.context.ContextDescriptor
import tech.kzen.lib.common.exec.engine.Execution
import tech.kzen.lib.common.exec.engine.context.BindingLookup
import tech.kzen.lib.common.exec.engine.context.InitialBinding
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import kotlin.reflect.KClass


/**
 * The call-site half of ambient context (logic-spec §6), shared by every flavour that can host a child Logic:
 * turn a hosting element's `contexts:` map into the [InitialBinding]s its callee starts with, and check that a
 * value satisfies the contract of the Context it is being bound under.
 *
 * Flavour-neutral on purpose. A Script's `RunStep` and a Flow's logic-host vertex ask exactly the same
 * question — *what does this call supply to that callee* — and the only thing that differs is how each finds
 * the element it is currently running. Keeping the resolution here means the two cannot drift: a fix to the
 * missing-vs-null distinction, or to what conformance admits, lands once for both.
 *
 * The `contexts:` map itself is read by the CALLER, from notation, not passed through any hosting signature —
 * see [tech.kzen.auto.common.objects.document.logic.context.LogicContextConventions.stepCallContexts]. Callers
 * memoize that read per element; this object holds no state of its own and no cache.
 */
object ContextCallSite {
    //-----------------------------------------------------------------------------------------------------------------
    /**
     * Resolve [callBindings] against the scope as it stands right now — what the caller supplies to the child
     * for this call and no longer.
     *
     * **Every failure here is attributed to the CALLING element**, which is the point of resolving before the
     * child exists: a source nothing bound is a mistake in the call, and surfacing it inside the callee — as
     * "uses X: not bound", partway through a document that is not the one at fault — is exactly the
     * misattribution the map is meant to remove.
     */
    fun initialBindings(execution: Execution, callBindings: List<ContextCallBinding>): List<InitialBinding> {
        return callBindings.map { callBinding ->
            val target = callBinding.target
                ?: error("Supplies '${callBinding.targetReference}', which is not a context")

            val source = callBinding.source
                ?: error("${target.label()} is mapped to '${callBinding.sourceReference}', " +
                        "which is not a context")

            // Presence-preserving, and the distinction is real rather than pedantic: a Context whose contract
            // is nullable may legitimately hold null, so "nothing is bound at the source" and "the source
            // holds null" are different mistakes and only the first is always one.
            val value = when (val lookup = execution.binding(ContextAddressing.keyOf(source))) {
                BindingLookup.Missing ->
                    error("${target.label()} is mapped to ${source.label()}, which is not bound")

                is BindingLookup.Present ->
                    lookup.value
            }

            // The TARGET's contract is what the value has to satisfy — the callee is what will read it — and
            // this is the definitive check whatever the source declared, since the raw bind surface can put a
            // value under a key without ever consulting a declaration. It also covers the nullable case: a
            // null passes only when the callee's slot admits one.
            checkBindConformance(target, value)

            InitialBinding(ContextAddressing.keyOf(target), value)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * The typed bind check, centralized rather than left to each binder: a `String` bound to a Context whose
     * contract is `RemoteWebDriver` otherwise surfaces as a `ClassCastException` inside a browser step several
     * steps away from the mistake that caused it.
     *
     * Runtime checking is **raw class plus nullability only**. JVM erasure means an arbitrary `List<*>` cannot
     * prove its element types by inspection, so this deliberately makes no claim about nested generics — full
     * generic conformance comes from source metadata, where a source type exists to compare. The raw
     * [Execution.bind] surface stays the unchecked escape hatch.
     */
    fun checkBindConformance(descriptor: ContextDescriptor, value: Any?) {
        if (value == null) {
            check(descriptor.type.nullable) {
                "${descriptor.label()} holds ${descriptor.type.toSimple()}, which is not nullable"
            }
            return
        }

        val declaredClassName = descriptor.type.className.asString()
        if (declaredClassName == TypeMetadata.any.className.asString()) {
            return
        }

        check(conformsToRawClass(value, declaredClassName)) {
            "${descriptor.label()} holds ${descriptor.type.toSimple()}, " +
                    "but ${value::class.qualifiedName} was bound"
        }
    }


    // Walks the VALUE's own Kotlin supertype names rather than loading the declared class: TypeMetadata carries
    // Kotlin names (`kotlin.String`, not `java.lang.String`), and the two namespaces do not line up for the
    // mapped built-ins. A hierarchy reflection cannot walk (a synthetic or proxy class) answers TRUE — an
    // unverifiable type must not fail a bind that would otherwise have worked.
    private fun conformsToRawClass(value: Any, declaredClassName: String): Boolean {
        return runCatching {
            val seen = HashSet<KClass<*>>()
            val frontier = ArrayDeque<KClass<*>>()
            frontier.add(value::class)

            while (frontier.isNotEmpty()) {
                val current = frontier.removeFirst()
                if (! seen.add(current)) {
                    continue
                }
                if (current.qualifiedName == declaredClassName) {
                    return@runCatching true
                }
                current.supertypes.mapNotNullTo(frontier) { it.classifier as? KClass<*> }
            }
            false
        }.getOrDefault(true)
    }
}
