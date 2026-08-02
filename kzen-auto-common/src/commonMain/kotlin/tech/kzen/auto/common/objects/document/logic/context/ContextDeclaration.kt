package tech.kzen.auto.common.objects.document.logic.context

import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.reflect.Reflect


/**
 * The instance behind a `is: Context` declaration. Inert at run time — everything that reads a Context reads
 * notation, through [ContextConventions] — but it is what makes a declaration a **concrete object** rather
 * than an abstract archetype, and that is the point.
 *
 * An abstract declaration had to carry `abstract: true` forever, because its `class:` named the VALUE type
 * and `GraphCreator` would otherwise try to construct a `RemoteWebDriver`. Pushing that obligation onto an
 * editor that writes declarations means the editor has to remember it forever. As a concrete object the value
 * contract moves to `type:` where it belongs, the class named here is one the graph can actually build, and a
 * declaration becomes the same shape as a `ParameterBinding` — so rename-refactor, reordering and per-entry
 * editing all come from machinery that already exists.
 *
 * Lives in the common module so the client can instantiate one too: a user's declarations sit in an ordinary
 * project document, and the client graph builds those.
 */
@Reflect
class ContextDeclaration(
    /** What may be bound here: class, generics and nullability. Not a lookup key — see [ContextAddressing]. */
    val type: TypeMetadata,

    /** Names one member of the family when this declaration is one of several (`primary` / `reporting`). */
    val qualifier: String,

    /** An explicit runtime family, replacing the one derived from [type] — a stable alias for raw interop. */
    val key: String
)
