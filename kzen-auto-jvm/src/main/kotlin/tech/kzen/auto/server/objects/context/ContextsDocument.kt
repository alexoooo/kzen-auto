package tech.kzen.auto.server.objects.context

import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.lib.common.reflect.Reflect


/**
 * The `main` object backing a Contexts document (`is: Contexts`) — a user's own Context declarations.
 *
 * Carries no state and no behaviour, deliberately. Context discovery is graph-wide
 * ([tech.kzen.auto.common.objects.document.logic.context.ContextConventions.allContexts] scans every object
 * for an `is: Context` ancestor), so nothing ever asks this document for its contents; the declarations are
 * found whether or not anyone holds this object. The `contexts` branch is declared in the archetype's `meta:`
 * for the editor and for `by: NestedList` membership, and — as with ScriptDocument's `steps` — a meta entry
 * with no matching constructor parameter is simply not injected.
 */
@Reflect
class ContextsDocument: DocumentArchetype()
