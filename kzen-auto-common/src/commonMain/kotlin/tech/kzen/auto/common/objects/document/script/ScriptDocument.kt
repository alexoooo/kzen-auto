package tech.kzen.auto.common.objects.document.script

import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.paradigm.imperative.api.ScriptStep
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.service.notation.NotationConventions


@Suppress("unused")
class ScriptDocument(
        val steps: List<ScriptStep>
):
        DocumentArchetype()
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val objectName = ObjectName("Script")

        val stepsAttributeName = AttributeName("steps")
        val stepsAttributePath = AttributePath.ofName(stepsAttributeName)


        fun isScript(/*documentPath: DocumentPath,*/ documentNotation: DocumentNotation): Boolean {
            val mainNotation = documentNotation.objects.notations[AutoConventions.mainPath]
                    ?: return false

            val isValue = mainNotation.get(NotationConventions.isAttributeName)?.asString()
                    ?: return false

            return isValue == objectName.value
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
                    .documents[containingObjectLocation.documentPath]!!
                    .objects
                    .notations
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

            return AutoConventions.randomAnonymous()
        }
    }
}
