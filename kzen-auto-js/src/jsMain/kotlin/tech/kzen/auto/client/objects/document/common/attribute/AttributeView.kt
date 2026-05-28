package tech.kzen.auto.client.objects.document.common.attribute

import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName


abstract class AttributeView(
    private val objectLocation: ObjectLocation
): ReactWrapper<AttributeViewProps> {
    //-----------------------------------------------------------------------------------------------------------------
    fun name(): ObjectName {
        return objectLocation.objectPath.name
    }
}
