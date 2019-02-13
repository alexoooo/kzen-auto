package tech.kzen.auto.common.objects.service

import tech.kzen.lib.common.api.model.AttributeName
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.api.model.ObjectReference


// TODO: change to ClientActionManager?
@Suppress("unused")
class ActionManager(
        private val actions: List<ObjectLocation>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // TODO: normalize class paths for nested classes between client and server
        const val actionParent = "Action"
        val className = "tech.kzen.auto.common.objects.service.ActionManager"

        val creatorReference = ObjectReference.parse("ActionManager.creator/Creator")

        val actionsAttribute = AttributeName("actions")

//        private const val locationKey = "location"
//
//        private const val valueCodecKey = "valueCodec"
//        private val valueCodecPath = AttributePath.ofAttribute(AttributeName(valueCodecKey))
//
//        private const val detailCodecKey = "detailCodec"
//        private val detailCodecPath = AttributePath.ofAttribute(AttributeName(detailCodecKey))
    }


    //-----------------------------------------------------------------------------------------------------------------
//    data class Handle(
//            val location: ObjectLocation,
//            val valueCodec: ResultCodec,
//            val detailCodec: ResultCodec
//    )


    //-----------------------------------------------------------------------------------------------------------------
    fun actionLocations(): List<ObjectLocation> {
        return actions
    }
}