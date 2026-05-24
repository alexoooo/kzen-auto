package tech.kzen.auto.client.objects.document.custom.view.obj

import tech.kzen.auto.common.objects.document.custom.CustomConventions
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.metadata.ObjectMetadata
import tech.kzen.lib.common.service.notation.NotationConventions


//---------------------------------------------------------------------------------------------------------------------
data class CustomObjectInfo(
    val objectMetadata: ObjectMetadata?,
    val isAbstract: Boolean,
    val isLogic: Boolean,
    val isDetached: Boolean,
    val isExported: Boolean
) {
    companion object {
        fun isAbstract(objectLocation: ObjectLocation, graphStructure: GraphStructure): Boolean =
            graphStructure.graphNotation
                .directAttribute(objectLocation, NotationConventions.abstractAttributePath)
                ?.asBoolean()
                ?: false


        fun derive(
            objectLocation: ObjectLocation,
            graphStructure: GraphStructure,
            exportMembership: Map<ObjectLocation, *>
        ): CustomObjectInfo {
            val objectMetadata = graphStructure.graphMetadata.objectMetadata[objectLocation]
            val isLogic = objectMetadata?.tags?.contains(CustomConventions.logicTag) ?: false
            val isDetached = objectMetadata?.tags?.contains(CustomConventions.detachedTag) ?: false
            val isExported = objectLocation in exportMembership

            return CustomObjectInfo(
                objectMetadata,
                isAbstract(objectLocation, graphStructure),
                isLogic,
                isDetached,
                isExported)
        }
    }
}
