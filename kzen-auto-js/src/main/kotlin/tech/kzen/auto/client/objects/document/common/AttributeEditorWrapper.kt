package tech.kzen.auto.client.objects.document.common

import react.RProps
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.structure.metadata.model.AttributeMetadata
import tech.kzen.lib.common.structure.notation.model.AttributeNotation


abstract class AttributeEditorWrapper(
        private val objectLocation: ObjectLocation
): ReactWrapper<AttributeEditorWrapper.Props> {
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var objectLocation: ObjectLocation,
            var attributeName: AttributeName,
            var attributeMetadata: AttributeMetadata,
            var attributeNotation: AttributeNotation
    ): RProps


    //-----------------------------------------------------------------------------------------------------------------
    fun name(): ObjectName {
        return objectLocation.objectPath.name
    }
}