package tech.kzen.auto.common.objects.document.script

import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.paradigm.imperative.api.ExecutionAction
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.auto.common.util.NameConventions
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.notation.edit.InsertObjectInListAttributeCommand
import tech.kzen.lib.common.structure.notation.model.ObjectNotation
import tech.kzen.lib.common.structure.notation.model.PositionIndex


@Suppress("unused")
class ScriptDocument(
        val steps: List<ExecutionAction>,
        objectLocation: ObjectLocation
):
        DocumentArchetype(objectLocation)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val stepsAttributeName = AttributeName("steps")
        val stepsAttributePath = AttributePath.ofName(stepsAttributeName)


        fun createCommand(
                containingObjectLocation: ObjectLocation,
                containingAttributePath: AttributePath,
                indexInContainingAttribute: Int,
                archetypeObjectLocation: ObjectLocation,
                graphStructure: GraphStructure
        ): InsertObjectInListAttributeCommand {
            val newName = findNextAvailable(
                    containingObjectLocation, archetypeObjectLocation, graphStructure)

            // NB: +1 offset for main Script object
            val endOfDocumentPosition = graphStructure
                    .graphNotation
                    .documents
                    .get(containingObjectLocation.documentPath)!!
                    .objects
                    .values
                    .size

            val objectNotation = ObjectNotation.ofParent(
                    archetypeObjectLocation.toReference().name)

            return InsertObjectInListAttributeCommand(
                    containingObjectLocation,
                    containingAttributePath,
                    PositionIndex(indexInContainingAttribute),
                    newName,
                    PositionIndex(endOfDocumentPosition),
                    objectNotation
            )
        }


        fun findNextAvailable(
                containingObjectLocation: ObjectLocation,
                archetypeObjectLocation: ObjectLocation,
                graphStructure: GraphStructure
        ): ObjectName {
            val namePrefix = graphStructure
                    .graphNotation
                    .transitiveAttribute(archetypeObjectLocation, AutoConventions.titleAttributePath)
                    ?.asString()
                    ?: archetypeObjectLocation.objectPath.name.value

            val directObjectName = ObjectName(namePrefix)

            val documentObjectNames = graphStructure
                    .graphNotation
                    .documents
                    .get(containingObjectLocation.documentPath)!!
                    .objects
                    .values
                    .keys
                    .map { it.name }
                    .toSet()

            if (directObjectName !in documentObjectNames) {
                return directObjectName
            }

            for (i in 2 .. 1000) {
                val numberedObjectName = ObjectName("$namePrefix $i")

                if (numberedObjectName !in documentObjectNames) {
                    return numberedObjectName
                }
            }

            return NameConventions.randomAnonymous()
        }
    }
}
