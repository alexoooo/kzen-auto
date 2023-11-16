package tech.kzen.auto.client.objects.document.graph.edit

import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName


abstract class AttributeEditorOld(
    private val objectLocation: ObjectLocation
): ReactWrapper<AttributeEditorPropsOld> {
    //-----------------------------------------------------------------------------------------------------------------
//    class Props(
//            var objectLocation: ObjectLocation,
//            var attributeName: AttributeName,
//            var attributeMetadata: AttributeMetadata,
//            var attributeNotation: AttributeNotation?
//    ): react.Props


    //-----------------------------------------------------------------------------------------------------------------
    fun name(): ObjectName {
        return objectLocation.objectPath.name
    }
}