package tech.kzen.auto.common.objects.document.custom

import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.notation.NotationConventions


object PrototypeConventions {
    val prototypeObjectName: ObjectName = ObjectName("Prototype")


    fun listPrototypes(graphNotation: GraphNotation): List<ObjectLocation> {
        return graphNotation.objectLocations.filter { location ->
            val isAttribute = graphNotation
                .directAttribute(location, NotationConventions.isAttributePath)
                ?.asString()
            isAttribute == prototypeObjectName.value
        }
    }
}
