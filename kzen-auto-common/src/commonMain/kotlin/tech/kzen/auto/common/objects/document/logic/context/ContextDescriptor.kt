package tech.kzen.auto.common.objects.document.logic.context

import tech.kzen.lib.common.model.location.ObjectLocation


/**
 * A `is: Context` notation object read as data: the run-scoped resource kind a Logic can provide, require or
 * release. Flavour-neutral and plugin-extensible — nothing enumerates the known Contexts, they are discovered
 * from the graph by inheritance.
 *
 * [key] is the ENGINE resource key, and it is a global namespace shared with the raw-string resource API
 * ([tech.kzen.auto.server.objects.script.api.StepExecution.openResource]-style callers): same key means same
 * registration, which is exactly what lets a typed step and a raw one interoperate. Two Contexts may therefore
 * alias; [LogicContextAnalysis] reports that graph-wide as a warning rather than preventing it.
 *
 * A key may be qualified at run time as `"<key>:<qualifier>"` (a SUT addressed by name). An export covers the
 * whole family; each qualifier is an independent registration.
 */
data class ContextDescriptor(
    /** The Context object itself — the identity a `provides` / `requires` / `releases` / `exports` entry names. */
    val location: ObjectLocation,

    /** The engine resource key (family), e.g. `browser` or `sut`. */
    val key: String,

    /** Qualified name of the value class a provider registers, for the typed read affordance; may be blank. */
    val valueClass: String,

    val title: String,
    val icon: String,
    val description: String
) {
    /** The display name: the declared [title], falling back to the object's own name. */
    fun label(): String {
        return title.ifEmpty { location.objectPath.name.value }
    }
}
