package tech.kzen.auto.common.objects.document

import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPathMap
import tech.kzen.lib.common.model.structure.notation.DocumentObjectNotation
import tech.kzen.lib.common.model.structure.notation.ObjectNotation
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.platform.collect.persistentMapOf


object DocumentCreator {
//    object Registry {
//        private val creators = mutableMapOf<ObjectLocation, () -> DocumentObjectNotation>()
//
//        @Synchronized
//        fun register(archetypeLocation: ObjectLocation, creator: () -> DocumentObjectNotation) {
//            check(archetypeLocation !in creators) {
//                "Document Archetype location already used: $archetypeLocation"
//            }
//            creators[archetypeLocation] = creator
//        }
//
//
//        @Synchronized
//        fun getOrDefault(
//            archetypeLocation: ObjectLocation
//        ): () -> DocumentObjectNotation {
//            val creator = creators[archetypeLocation]
////            if (creator == )
//
//        }
//    }


    fun newDocument(
        archetypeLocation: ObjectLocation
    ): DocumentObjectNotation {
        val mainObjectNotation = ObjectNotation.ofParent(archetypeLocation.objectPath.name)

        val objectNotations = ObjectPathMap(
            persistentMapOf(
                NotationConventions.mainObjectPath to mainObjectNotation)
        )

        return DocumentObjectNotation(objectNotations)
    }
}