package tech.kzen.auto.common.objects.document.logic.context


/**
 * One entry of a RunStep's `contexts:` map: the caller wiring one of its own Context declarations into a slot
 * the callee declares, for the duration of that call.
 *
 * **A mapping between namespaces, not a shared name.** [target] is named in the CALLEE's world and [source] in
 * the caller's, and they deliberately need not agree: a caller holding `Browser A` and `Browser B` (two
 * declarations of one family, separated by their qualifiers) can drive a generic sub-Script that declares one
 * unqualified `Browser` slot and knows nothing about either. At run time the value is read under [source]'s
 * key and installed under [target]'s, so that asymmetry costs nothing and is what keeps the callee generic —
 * it is reusable against a single-browser caller with no `contexts:` entry at all, unedited.
 *
 * Both sides are kept as their raw reference strings ALONGSIDE the resolved descriptor, and the descriptors are
 * nullable, because the three consumers want different things from a bad entry: the static analysis warns
 * naming the string the author actually typed, the runtime refuses to launch the call, and neither can say
 * anything useful about an entry it silently dropped.
 *
 * **[targetReference] is not rewritten by a rename**, and that is a property of where it sits rather than a
 * choice: it is a notation map KEY, and kzen-lib's refactor walks attribute *values* (`ObjectDefinition`'s
 * traversal turns a map key into a path segment and recurses into the value alone) — there is no rename-a-key
 * command in the notation vocabulary at all. Renaming a Context therefore updates the callee's own
 * `context.requires` and leaves this key behind, which surfaces as the ordinary unsatisfied-requires ERROR on
 * the calling RunStep plus a dangling warning — loud, attributed to the step that must be re-pointed, and
 * never a silent mis-wire. [sourceReference], sitting in value position, does propagate.
 */
data class ContextCallBinding(
    /** The callee's slot, as written in notation — the map key. */
    val targetReference: String,

    /** [targetReference] resolved, or null when it names nothing that is a Context. */
    val target: ContextDescriptor?,

    /** The caller's declaration supplying the value — the map value. */
    val sourceReference: String,

    /** [sourceReference] resolved, or null when it names nothing that is a Context. */
    val source: ContextDescriptor?
)
