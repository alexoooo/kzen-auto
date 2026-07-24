package tech.kzen.auto.client.objects.document.script.command

import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.ObjectNotation
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.cqrs.AddObjectCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.util.naming.NextAvailableName


@Reflect
class ScriptCommander(
    stepCommanders: List<ScriptStepCommander>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun findNextAvailable(
            containingObjectLocation: ObjectLocation,
            archetypeObjectLocation: ObjectLocation,
            graphStructure: GraphStructure
        ): ObjectName {
            val namePrefix = graphStructure
                .graphNotation
                .firstAttribute(archetypeObjectLocation, AutoConventions.titleAttributePath)
                ?.asString()
                ?: archetypeObjectLocation.objectPath.name.value

            val documentObjectNames: Set<ObjectName> = graphStructure
                .graphNotation
                .documents[containingObjectLocation.documentPath]!!
                .objects
                .notations
                .map
                .keys
                .map { it.name }
                .toSet()

            return NextAvailableName
                .find(namePrefix, separator = " ") { ObjectName(it) !in documentObjectNames }
                ?.let { ObjectName(it) }
                ?: AutoConventions.randomAnonymous()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The new step's location travels with the commands that create it, so the caller can act on the step it
    // just added without re-deriving the generated name.
    data class StepCreation(
        val objectLocation: ObjectLocation,
        val commands: List<NotationCommand>
    )


    //-----------------------------------------------------------------------------------------------------------------
    private val byArchetype: Map<ObjectLocation, ScriptStepCommander>


    //-----------------------------------------------------------------------------------------------------------------
    init {
        val builder = mutableMapOf<ObjectLocation, ScriptStepCommander>()
        for (stepCommander in stepCommanders) {
            for (archetype in stepCommander.archetypes()) {
                builder[archetype] = stepCommander
            }
        }
        byArchetype = builder
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun createStep(
        containingAttributeLocation: AttributeLocation,
        indexInContainingAttribute: Int,
        archetypeObjectLocation: ObjectLocation,
        graphStructure: GraphStructure
    ): StepCreation {
        val containingObjectLocation = containingAttributeLocation.objectLocation

        val newName = findNextAvailable(
            containingObjectLocation, archetypeObjectLocation, graphStructure)

        // The step is a child object nested under the branch attribute; its order in the branch is its
        // position in the document, so insert it at the document index for the requested branch slot.
        val newObjectLocation = ObjectLocation(
            containingObjectLocation.documentPath,
            containingObjectLocation.objectPath.nest(
                containingAttributeLocation.attributePath, newName))

        val insertDocumentIndex = insertionDocumentIndex(
            graphStructure, containingAttributeLocation, indexInContainingAttribute)

        val objectNotation = ObjectNotation.ofParent(
            archetypeObjectLocation.objectPath.name)

        val command = AddObjectCommand(
            newObjectLocation,
            PositionRelation.at(insertDocumentIndex),
            objectNotation)

        val stepCommander = byArchetype[archetypeObjectLocation]

        val commands =
            if (stepCommander != null) {
                listOf(command) + stepCommander.additionalCommands(
                    newObjectLocation, insertDocumentIndex, graphStructure)
            }
            else {
                listOf(command)
            }

        return StepCreation(newObjectLocation, commands)
    }


    // The document index at which to insert a new step so it lands at the requested branch slot: before
    // the sibling currently occupying that slot, or after the last sibling's whole subtree, or right
    // after the containing object when the branch is empty.
    private fun insertionDocumentIndex(
        graphStructure: GraphStructure,
        containingAttributeLocation: AttributeLocation,
        indexInContainingAttribute: Int
    ): Int {
        val documentPath = containingAttributeLocation.objectLocation.documentPath
        val documentNotation = graphStructure.graphNotation.documents[documentPath]!!
        val siblings = ScriptConventions.orderedDirectChildLocations(
            graphStructure.graphNotation, containingAttributeLocation)

        return when {
            siblings.isEmpty() ->
                documentNotation.indexOf(
                    containingAttributeLocation.objectLocation.objectPath).value + 1

            indexInContainingAttribute < siblings.size ->
                documentNotation.indexOf(siblings[indexInContainingAttribute].objectPath).value

            else -> {
                val lastSibling = siblings.last().objectPath
                documentNotation.objects.notations.map.keys.indexOfLast {
                    it == lastSibling || it.startsWith(lastSibling)
                } + 1
            }
        }
    }
}