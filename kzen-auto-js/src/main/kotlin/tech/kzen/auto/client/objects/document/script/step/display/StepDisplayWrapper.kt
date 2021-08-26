package tech.kzen.auto.client.objects.document.script.step.display

import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName


abstract class StepDisplayWrapper(
        private val objectLocation: ObjectLocation
): ReactWrapper<StepDisplayProps> {
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