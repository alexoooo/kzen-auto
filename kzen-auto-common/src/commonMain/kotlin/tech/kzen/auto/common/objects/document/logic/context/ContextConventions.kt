package tech.kzen.auto.common.objects.document.logic.context

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation


/**
 * Reads `is: Context` notation objects as [ContextDescriptor]s. Flavour-neutral: nothing here knows about
 * Script steps, and nothing enumerates the known Contexts — a plugin's own Context is found by the same
 * inheritance-chain filter as `BrowserContext`.
 *
 * A Context is data, never instantiated (`abstract: true`); the attributes that NAME one are declared
 * `by: Nominal` (weak references), so a reference to a Context that does not resolve degrades to a
 * validation message rather than failing the referring object's definition. That is why every read here is
 * nullable-tolerant.
 */
object ContextConventions {
    //-----------------------------------------------------------------------------------------------------------------
    val contextObjectName = ObjectName("Context")

    val keyAttributeName = AttributeName("key")
    val classAttributeName = AttributeName("class")
    val titleAttributeName = AttributeName("title")
    val iconAttributeName = AttributeName("icon")
    val descriptionAttributeName = AttributeName("description")


    //-----------------------------------------------------------------------------------------------------------------
    /** Does [objectLocation] inherit the `Context` archetype? Stale-location tolerant (see the guard). */
    fun isContext(graphNotation: GraphNotation, objectLocation: ObjectLocation): Boolean {
        if (objectLocation !in graphNotation.coalesce) {
            return false
        }
        return graphNotation
            .inheritanceChain(objectLocation)
            .any { it.objectPath.name == contextObjectName }
    }


    /** [objectLocation] read as a Context, or null when it is not one (or no longer exists). */
    fun descriptorOrNull(graphNotation: GraphNotation, objectLocation: ObjectLocation): ContextDescriptor? {
        if (! isContext(graphNotation, objectLocation)) {
            return null
        }
        return ContextDescriptor(
            objectLocation,
            key = scalarOrEmpty(graphNotation, objectLocation, keyAttributeName),
            valueClass = scalarOrEmpty(graphNotation, objectLocation, classAttributeName),
            title = scalarOrEmpty(graphNotation, objectLocation, titleAttributeName),
            icon = scalarOrEmpty(graphNotation, objectLocation, iconAttributeName),
            description = scalarOrEmpty(graphNotation, objectLocation, descriptionAttributeName))
    }


    /**
     * Resolve a notated reference (a `provides:` scalar, a `context.exports` list entry) to its Context.
     * [host] is the referring object, so a project-local Context resolves by bare name exactly as any other
     * reference does. Null when the reference names nothing, or names something that is not a Context — both
     * are reported by [LogicContextAnalysis] as a dangling reference.
     */
    fun resolveOrNull(
        graphNotation: GraphNotation,
        reference: String,
        host: ObjectLocation
    ): ContextDescriptor? {
        if (reference.isEmpty()) {
            return null
        }
        val objectLocation = graphNotation.coalesce.locateOptional(
            ObjectReference.parse(reference), ObjectReferenceHost.ofLocation(host))
            ?: return null
        return descriptorOrNull(graphNotation, objectLocation)
    }


    /** Every Context in the graph — the editor's picker list, and the duplicate-key check's input. */
    fun allContexts(graphNotation: GraphNotation): List<ContextDescriptor> {
        return graphNotation
            .coalesce
            .map
            .keys
            .mapNotNull { descriptorOrNull(graphNotation, it) }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The nullable AttributePath overload of firstAttribute, deliberately: the AttributeName overload THROWS
    // when the attribute is absent, and every Context attribute is optional.
    private fun scalarOrEmpty(
        graphNotation: GraphNotation,
        objectLocation: ObjectLocation,
        attributeName: AttributeName
    ): String {
        val notation = graphNotation.firstAttribute(
            objectLocation, AttributePath.ofName(attributeName))
        return (notation as? ScalarAttributeNotation)?.value ?: ""
    }
}
