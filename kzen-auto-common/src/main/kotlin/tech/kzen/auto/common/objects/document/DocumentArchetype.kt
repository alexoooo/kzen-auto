package tech.kzen.auto.common.objects.document

import tech.kzen.lib.common.api.model.DocumentMap
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.api.model.ObjectName
import tech.kzen.lib.common.structure.notation.NotationConventions
import tech.kzen.lib.common.structure.notation.model.DocumentNotation
import tech.kzen.lib.common.structure.notation.model.ObjectNotation


abstract class DocumentArchetype(
        private val objectLocation: ObjectLocation
) {
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