package tech.kzen.auto.common.objects.document.custom

import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.notation.NotationConventions


object CustomConventions {
    private val customDocumentObjectName = ObjectName("CustomDocument")

    val prototypeObjectName: ObjectName = ObjectName("Prototype")

    val logicAttributeName = AttributeName("logic")
    val logicAttributePath = AttributePath.ofName(logicAttributeName)


    fun isManaged(attributeName: AttributeName): Boolean {
        return AutoConventions.isManaged(attributeName) ||
            attributeName == logicAttributeName
    }


    fun isCustomDocument(documentNotation: DocumentNotation): Boolean {
        val mainObjectNotation =
            documentNotation.objects.notations[NotationConventions.mainObjectPath]
                ?: return false

        val mainObjectIs =
            mainObjectNotation.get(NotationConventions.isAttributeName)?.asString()
                ?: return false

        return mainObjectIs == customDocumentObjectName.value
    }


    fun listPrototypes(graphNotation: GraphNotation): List<ObjectLocation> {
        return graphNotation.objectLocations.filter { location ->
            val isAttribute = graphNotation
                .directAttribute(location, NotationConventions.isAttributePath)
                ?.asString()
            isAttribute == prototypeObjectName.value
        }
    }
}
