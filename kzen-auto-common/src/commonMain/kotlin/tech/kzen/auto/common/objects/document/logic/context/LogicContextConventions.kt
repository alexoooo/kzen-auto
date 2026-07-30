package tech.kzen.auto.common.objects.document.logic.context

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.service.notation.NotationConventions


/**
 * The context declarations a Logic document and its steps carry, read straight off notation:
 *
 * - a DOCUMENT declares `context: { exports: [...], requires: [...] }` — the Contexts it offers upward to its
 *   caller, and the ones a caller must already have provided. A provide the document does not export is
 *   private to it: ownership is offered by the provider, never claimed by an ancestor (logic-spec §6). The
 *   retired `context.slots` key is read only to warn about it — see [legacyDocumentSlotReferences];
 * - a STEP declares `provides: <Context>` (it opens the resource), `requires: [<Context>]` (it reads one),
 *   or `releases: <Context>` (it is a closer).
 *
 * All four are declared `by: Nominal` in their archetypes' `meta:` (weak references — the standard mechanism
 * for object-naming data attributes, like `Custom.exports` and `RunStep.instructions`): never
 * constructor-injected, a dangling or abstract-targeted entry is a validation message rather than a
 * definition failure, and renaming a Context propagates into the declarations. Body rendering is suppressed
 * via [isContextDeclaration].
 *
 * INHERITANCE COMES FREE. [GraphNotation.firstAttribute] walks the *linearized* inheritance chain and returns
 * the closest ancestor's value, so a user's step object `is: BrowserClickStep` reads the archetype's
 * `requires` with no manual walk. Note what "closest wins" implies: a concrete archetype declaring its own
 * `requires` REPLACES an inherited list rather than extending it. That is the intended rule — no first-party
 * case needs merging — but a plugin author combining two requiring mix-ins gets only one, so it is stated
 * rather than discovered.
 */
object LogicContextConventions {
    //-----------------------------------------------------------------------------------------------------------------
    val contextAttributeName = AttributeName("context")
    val contextAttributePath = AttributePath.ofName(contextAttributeName)

    val exportsSegment = AttributeSegment.ofKey("exports")
    val requiresSegment = AttributeSegment.ofKey("requires")

    val documentExportsAttributePath = contextAttributePath.nest(exportsSegment)
    val documentRequiresAttributePath = contextAttributePath.nest(requiresSegment)

    // The retired `context.slots` key has no effect. Read solely so the analysis and the signature editor can
    // WARN that a declaration still on disk is inert — deliberately not auto-migrated to `exports`, which sits
    // on the opposite document (`slots` claimed ownership on the consumer, `exports` offers it on the provider),
    // so a mechanical rewrite would move the wrong thing.
    val legacySlotsSegment = AttributeSegment.ofKey("slots")
    val legacySlotsAttributePath = contextAttributePath.nest(legacySlotsSegment)

    val providesAttributeName = AttributeName("provides")
    val providesAttributePath = AttributePath.ofName(providesAttributeName)

    val requiresAttributeName = AttributeName("requires")
    val requiresAttributePath = AttributePath.ofName(requiresAttributeName)

    val releasesAttributeName = AttributeName("releases")
    val releasesAttributePath = AttributePath.ofName(releasesAttributeName)


    /**
     * True for the meta-declared context declaration attributes, so the step-body editor can skip them —
     * they are managed by the header badges and the document's ContextSignatureEditor, and their types
     * (ObjectLocation / List / Map) have no generic editor anyway.
     */
    fun isContextDeclaration(attributeName: AttributeName): Boolean {
        return attributeName == providesAttributeName ||
                attributeName == requiresAttributeName ||
                attributeName == releasesAttributeName ||
                attributeName == contextAttributeName
    }


    //-------------------------------------------------------------------------------------------------- document level
    /**
     * The Contexts [documentPath]'s `main` object declares it EXPORTS — offered upward, so a provide of one of
     * them climbs past this document's frame to its caller (and onward while each caller exports it too). A
     * Context absent here is private to this document, disposed at its settle. The engine half is
     * `Execution.declareExport`, called once per entry at `Logic.run` start.
     */
    fun documentExports(graphNotation: GraphNotation, documentPath: DocumentPath): List<ContextDescriptor> {
        return documentContexts(graphNotation, documentPath, documentExportsAttributePath)
    }


    /** The Contexts [documentPath]'s `main` object declares a CALLER must already have provided. */
    fun documentRequires(graphNotation: GraphNotation, documentPath: DocumentPath): List<ContextDescriptor> {
        return documentContexts(graphNotation, documentPath, documentRequiresAttributePath)
    }


    /**
     * The whole `context` map as notation, so an editor that upserts the map wholesale can carry through keys
     * it does not recognize instead of eating them. Reads through inheritance like every other declaration, so
     * a document with no local `context` yields the `Script` archetype's empty map rather than inventing one.
     */
    fun documentContextEntries(
        graphNotation: GraphNotation,
        documentPath: DocumentPath
    ): Map<AttributeSegment, AttributeNotation> {
        val mainLocation = ObjectLocation(documentPath, NotationConventions.mainObjectPath)
        if (mainLocation !in graphNotation.coalesce) {
            return mapOf()
        }
        return (graphNotation.firstAttribute(mainLocation, contextAttributePath) as? MapAttributeNotation)
            ?.map
            ?: mapOf()
    }


    /**
     * The raw reference strings of a retired `context.slots` declaration, non-empty only for notation written
     * against CTX. Raw rather than resolved because the warning needs to fire even when the entries dangle —
     * and because nothing consumes them: the key has no effect. See [legacySlotsAttributePath].
     */
    fun legacyDocumentSlotReferences(graphNotation: GraphNotation, documentPath: DocumentPath): List<String> {
        return documentContextReferences(graphNotation, documentPath, legacySlotsAttributePath)
    }


    /**
     * The raw (unresolved) reference strings of a document-level declaration, so the analysis can tell a
     * dangling entry from an absent one — [documentExports] / [documentRequires] silently drop what does not
     * resolve.
     */
    fun documentContextReferences(
        graphNotation: GraphNotation,
        documentPath: DocumentPath,
        attributePath: AttributePath
    ): List<String> {
        val mainLocation = ObjectLocation(documentPath, NotationConventions.mainObjectPath)
        if (mainLocation !in graphNotation.coalesce) {
            return listOf()
        }
        return referenceList(graphNotation, mainLocation, attributePath)
    }


    //------------------------------------------------------------------------------------------------------ step level
    /** The Context [stepLocation] provides (opens), or null when it provides none. */
    fun stepProvides(graphNotation: GraphNotation, stepLocation: ObjectLocation): ContextDescriptor? {
        return singleContext(graphNotation, stepLocation, providesAttributePath)
    }


    /** The Contexts [stepLocation] reads — the ones the runtime gate gates on and the analysis ambers on. */
    fun stepRequires(graphNotation: GraphNotation, stepLocation: ObjectLocation): List<ContextDescriptor> {
        return stepContexts(graphNotation, stepLocation, requiresAttributePath)
    }


    /**
     * The Context [stepLocation] releases (it is a closer), or null. Never gated at run time and never
     * ambered: a closer's job is to make the absence true, so "already absent" is success. It exists so a
     * closer can resolve its Context argument-free, and so the analysis can drop the Context from
     * availability after it.
     */
    fun stepReleases(graphNotation: GraphNotation, stepLocation: ObjectLocation): ContextDescriptor? {
        return singleContext(graphNotation, stepLocation, releasesAttributePath)
    }


    /**
     * Every Context [stepLocation] declares in any role — `provides` ∪ `requires` ∪ `releases`. This is the
     * set the runtime resolves an argument-free typed read against: a step declaring exactly one resolves
     * without naming it, zero or several is an error the caller fixes by passing the Context explicitly.
     */
    fun stepDeclaredContexts(graphNotation: GraphNotation, stepLocation: ObjectLocation): List<ContextDescriptor> {
        val result = LinkedHashMap<ObjectLocation, ContextDescriptor>()
        stepProvides(graphNotation, stepLocation)?.let { result[it.location] = it }
        stepRequires(graphNotation, stepLocation).forEach { result[it.location] = it }
        stepReleases(graphNotation, stepLocation)?.let { result[it.location] = it }
        return result.values.toList()
    }


    /** The raw (unresolved) reference strings of a step-level declaration — see [documentContextReferences]. */
    fun stepContextReferences(
        graphNotation: GraphNotation,
        stepLocation: ObjectLocation,
        attributePath: AttributePath
    ): List<String> {
        if (stepLocation !in graphNotation.coalesce) {
            return listOf()
        }
        return referenceList(graphNotation, stepLocation, attributePath)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun documentContexts(
        graphNotation: GraphNotation,
        documentPath: DocumentPath,
        attributePath: AttributePath
    ): List<ContextDescriptor> {
        val mainLocation = ObjectLocation(documentPath, NotationConventions.mainObjectPath)
        if (mainLocation !in graphNotation.coalesce) {
            return listOf()
        }
        return referenceList(graphNotation, mainLocation, attributePath)
            .mapNotNull { ContextConventions.resolveOrNull(graphNotation, it, mainLocation) }
            .distinctBy { it.location }
    }


    private fun stepContexts(
        graphNotation: GraphNotation,
        stepLocation: ObjectLocation,
        attributePath: AttributePath
    ): List<ContextDescriptor> {
        if (stepLocation !in graphNotation.coalesce) {
            return listOf()
        }
        return referenceList(graphNotation, stepLocation, attributePath)
            .mapNotNull { ContextConventions.resolveOrNull(graphNotation, it, stepLocation) }
            .distinctBy { it.location }
    }


    private fun singleContext(
        graphNotation: GraphNotation,
        objectLocation: ObjectLocation,
        attributePath: AttributePath
    ): ContextDescriptor? {
        return stepContexts(graphNotation, objectLocation, attributePath).firstOrNull()
    }


    /**
     * A declaration read as reference strings. Tolerates both notation shapes — a list (`requires: [A, B]`)
     * and a bare scalar (`provides: A`) — so `provides` / `releases` / `requires` / `exports` share one reader.
     * The blessed shapes are scalar for the single-valued declarations (`provides` / `releases`) and list for
     * the multi-valued ones (`requires` / `context.exports` / `context.requires`), matching the archetypes'
     * `by: Nominal` meta declarations; either shape is safe (WeakAttributeDefiner handles both, and
     * kzen-lib's inferMetadata no longer promotes scalars naming abstract objects).
     *
     * Uses the NULLABLE [AttributePath] overload of `firstAttribute`: the [AttributeName] overload throws
     * when the attribute is absent, and every one of these is optional.
     */
    private fun referenceList(
        graphNotation: GraphNotation,
        objectLocation: ObjectLocation,
        attributePath: AttributePath
    ): List<String> {
        return when (val notation = graphNotation.firstAttribute(objectLocation, attributePath)) {
            is ListAttributeNotation ->
                notation.values.mapNotNull { (it as? ScalarAttributeNotation)?.value }.filter { it.isNotEmpty() }

            is ScalarAttributeNotation ->
                listOfNotNull(notation.value.takeIf { it.isNotEmpty() })

            else ->
                listOf()
        }
    }
}
