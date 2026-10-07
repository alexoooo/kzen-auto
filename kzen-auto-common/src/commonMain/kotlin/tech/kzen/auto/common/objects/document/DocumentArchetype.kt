package tech.kzen.auto.common.objects.document

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.notation.NotationConventions


abstract class DocumentArchetype {
    companion object {
        // Read as a reference: a document's `is` is normally the bare archetype name, but a qualified one
        // (`auto-common/common-document.yaml#CustomDocument`) names the same archetype
        private fun archetypeReference(
            graphNotation: GraphNotation,
            documentPath: DocumentPath
        ): ObjectReference? {
            val document = graphNotation.documents[documentPath]
                ?: return null

            val mainObject = document.objects.notations[NotationConventions.mainObjectPath]
                ?: return null

            return mainObject
                .attributes[NotationConventions.isAttributeName]
                ?.asString()
                ?.let { ObjectReference.parse(it) }
        }


        fun archetypeName(
            graphNotation: GraphNotation,
            documentPath: DocumentPath
        ): ObjectName? {
            return archetypeReference(graphNotation, documentPath)?.name?.objectName
        }


        // Null for an archetype the graph does not hold, so one broken document cannot take down every caller
        fun archetypeLocation(
            graphNotation: GraphNotation,
            documentPath: DocumentPath
        ): ObjectLocation? {
            val reference = archetypeReference(graphNotation, documentPath)
                ?: return null

            return graphNotation.coalesce.locateOptional(reference)
        }
    }
}
