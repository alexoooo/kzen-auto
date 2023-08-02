package tech.kzen.auto.client.objects.document.common.attribute

import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName


abstract class AttributeEditor(
    private val objectLocation: ObjectLocation
): ReactWrapper<AttributeEditor2Props> {
    //-----------------------------------------------------------------------------------------------------------------
    fun name(): ObjectName {
        return objectLocation.objectPath.name
    }
}