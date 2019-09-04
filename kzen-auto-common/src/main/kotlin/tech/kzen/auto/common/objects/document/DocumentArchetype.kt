package tech.kzen.auto.common.objects.document

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectPathMap
import tech.kzen.lib.common.structure.notation.NotationConventions
import tech.kzen.lib.common.structure.notation.model.DocumentNotation
import tech.kzen.lib.common.structure.notation.model.GraphNotation
import tech.kzen.lib.common.structure.notation.model.ObjectNotation
import tech.kzen.lib.platform.collect.persistentMapOf


abstract class DocumentArchetype(
//        private val objectLocation: ObjectLocation
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
                    .attributes
                    .values[NotationConventions.isAttributeName]
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


        fun newDocument(archetypeLocation: ObjectLocation): DocumentNotation {
            val mainObjectNotation = ObjectNotation.ofParent(archetypeLocation.objectPath.name)
            return DocumentNotation(ObjectPathMap(persistentMapOf(
                    NotationConventions.mainObjectPath to mainObjectNotation
            )),
                    null)
        }
    }


//    fun name(): ObjectName {
//        return objectLocation.objectPath.name
//    }


//    fun newDocument(): DocumentNotation {
//        val mainObjectNotation = ObjectNotation.ofParent(name())
//        return DocumentNotation(ObjectPathMap(persistentMapOf(
//                NotationConventions.mainObjectPath to mainObjectNotation
//        )))
//    }
}