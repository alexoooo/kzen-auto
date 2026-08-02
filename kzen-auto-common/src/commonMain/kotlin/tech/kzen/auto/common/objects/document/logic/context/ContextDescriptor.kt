package tech.kzen.auto.common.objects.document.logic.context

import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata


/**
 * A `is: Context` notation object read as data: a named slot in the ambient scope, what may be bound into it,
 * and where that lands in a frame's registry. Flavour-neutral and plugin-extensible — nothing enumerates the
 * known Contexts, they are discovered from the graph by inheritance.
 *
 * Three layers, deliberately separate ([ContextAddressing] derives the third):
 *
 * - [location] is the **nominal identity**: what a `binds` / `uses` / `context.exports` entry names, what
 *   rename-refactor rewrites, and what the picker lists. Two declarations are two authoring concepts even when
 *   they deliberately interoperate at run time.
 * - [type] is the **value contract**: class, generics and nullability. It filters editors and drives the
 *   conformance checks. It is not a lookup key.
 * - [key] and [qualifier] shape the **runtime address**. The family defaults from the canonical full [type];
 *   an explicit [key] replaces it as a stable alias for the raw-string API, which is what lets a typed step
 *   and a raw one interoperate. Two declarations may therefore resolve to one exact key — the explicit interop
 *   escape hatch, reported graph-wide by [LogicContextAnalysis] as an alias rather than prevented.
 */
data class ContextDescriptor(
    /** The Context object itself — the identity a `provides` / `requires` / `releases` / `exports` entry names. */
    val location: ObjectLocation,

    /** What may be bound here. */
    val type: TypeMetadata,

    /** One member of the family, named statically (`primary` / `reporting`); empty when the family is unqualified. */
    val qualifier: String,

    /** An explicit runtime family (`browser`, `sut`) replacing the one derived from [type]; may be blank. */
    val key: String,

    val title: String,
    val icon: String,
    val description: String
) {
    /** The display name: the declared [title], falling back to the object's own name. */
    fun label(): String {
        return title.ifEmpty { location.objectPath.name.value }
    }


    /** The value contract without package names, for a picker row that shows the full type on hover instead. */
    fun typeLabel(): String {
        return type.toSimple()
    }
}
