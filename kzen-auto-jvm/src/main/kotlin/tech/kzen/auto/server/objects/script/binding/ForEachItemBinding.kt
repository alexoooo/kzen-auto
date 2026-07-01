package tech.kzen.auto.server.objects.script.binding

import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.ScriptValueBinding
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.util.IterableElementTypeReflect
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.reflect.Reflect


/**
 * Exposes a ForEachStep's current loop item to nested steps by name, fully typed, without a body row
 * (replaces the old ForEachItemStep). Lives in the enclosing ForEach's `item` branch, so it is validated
 * and referenceable like a step but never executed. Its type is the element type of the ForEach's `items`
 * collection, resolved directly from that collection (NOT via the ForEach's own output type, which is the
 * List of the body's terminal type — reading it would be circular whenever the body references this item);
 * its value is supplied by the engine's loop at run time.
 */
@Reflect
class ForEachItemBinding(
    private val selfLocation: ObjectLocation
):
    ScriptValueBinding()
{
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition? {
        val forEachLocation = selfLocation.parent()
            ?: return ScriptStepDefinition.of(TupleDefinition.ofMain(LogicType.anyNullable))

        val graphNotation = scriptDefinitionContext.graphNotation

        // Resolve the enclosing ForEach's `items` reference to the collection step it points at.
        val itemsReference = (graphNotation
            .firstAttribute(forEachLocation, ScriptConventions.itemsAttributePath)
            as? ScalarAttributeNotation)
            ?.value
            ?: return ScriptStepDefinition.of(TupleDefinition.ofMain(LogicType.anyNullable))

        val itemsLocation = graphNotation.coalesce.locateOptional(
            ObjectReference.parse(itemsReference),
            ObjectReferenceHost.ofLocation(forEachLocation))
            ?: return ScriptStepDefinition.of(TupleDefinition.ofMain(LogicType.anyNullable))

        // Element type of that collection. Defer (null) until items has been validated so the element type
        // can refine past Any (ScriptValidator iterates to a fixpoint).
        val itemsType = scriptDefinitionContext.scriptValidation
            .stepValidations[itemsLocation.objectPath]?.typeMetadata
            ?: return null

        val metadata = elementTypeOf(itemsType)
        return ScriptStepDefinition.of(
            TupleDefinition.ofMain(LogicType(metadata)))
    }


    // The element type of the items collection: its first generic parameter when it has one
    // (List<X>, Set<X>, …); otherwise — for an Iterable that fixes its element type and so exposes no
    // generic parameter (IntRange : Iterable<Int>, a FormulaStep `1..100`) — recovered reflectively from
    // the class's Iterable<E> supertype. Falls back to Any when neither applies.
    private fun elementTypeOf(collectionType: TypeMetadata): TypeMetadata {
        collectionType.generics.firstOrNull()?.let { return it }

        return IterableElementTypeReflect.elementType(collectionType.className)
            ?: TypeMetadata.any
    }
}
