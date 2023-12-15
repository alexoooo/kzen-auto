package tech.kzen.auto.common.objects.document

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.notation.NotationConventions


abstract class DocumentArchetype {
    companion object {
        fun archetypeName(
            graphNotation: GraphNotation,
            documentPath: DocumentPath
        ): ObjectName? {
            val document = graphNotation.documents[documentPath]
                ?: return null

            val mainObject = document.objects.notations[NotationConventions.mainObjectPath]
                ?: return null

            return mainObject
                .attributes[NotationConventions.isAttributeName]
                ?.asString()
                ?.let { ObjectName(it) }
        }


        fun archetypeLocation(
            graphNotation: GraphNotation,
            documentPath: DocumentPath
        ): ObjectLocation? {
            val parentName = archetypeName(graphNotation, documentPath)
                ?: return null

            return graphNotation.coalesce.locate(ObjectReference.ofRootName(parentName))
        }
    }
}