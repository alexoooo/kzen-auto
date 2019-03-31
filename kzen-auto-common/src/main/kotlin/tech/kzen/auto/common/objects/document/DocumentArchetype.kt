package tech.kzen.auto.common.objects.document

import tech.kzen.lib.common.api.model.*
import tech.kzen.lib.common.structure.notation.NotationConventions
import tech.kzen.lib.common.structure.notation.model.DocumentNotation
import tech.kzen.lib.common.structure.notation.model.GraphNotation
import tech.kzen.lib.common.structure.notation.model.ObjectNotation


abstract class DocumentArchetype(
        private val objectLocation: ObjectLocation
) {
    companion object {
        fun archetypeName(
                graphNotation: GraphNotation,
                documentPath: DocumentPath
        ): ObjectName? {
            val document = graphNotation.documents.values[documentPath]
                    ?: return null

            val mainObject = document.objects.values[NotationConventions.mainObjectPath]
                    ?: return null

            return mainObject
                    .attributes[NotationConventions.isName]
                    ?.asString()
                    ?.let { ObjectName(it) }
        }

        fun archetypeLocation(
                graphNotation: GraphNotation,
                documentPath: DocumentPath
        ): ObjectLocation? {
            val parentName = archetypeName(graphNotation, documentPath)
                    ?: return null

            return graphNotation.coalesce.locate(ObjectReference.ofName(parentName))
        }
    }


    fun name(): ObjectName {
        return objectLocation.objectPath.name
    }


    fun newDocument(): DocumentNotation {
        val mainObjectNotation = ObjectNotation.ofParent(name())
        return DocumentNotation(DocumentMap(mapOf(
                NotationConventions.mainObjectPath to mainObjectNotation
        )))
    }
}