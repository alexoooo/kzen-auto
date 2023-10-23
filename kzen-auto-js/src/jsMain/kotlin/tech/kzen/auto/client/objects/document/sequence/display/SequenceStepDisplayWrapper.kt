package tech.kzen.auto.client.objects.document.sequence.display

import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName


abstract class SequenceStepDisplayWrapper(
    private val objectLocation: ObjectLocation
):
    ReactWrapper<SequenceStepDisplayProps>
{
    //-----------------------------------------------------------------------------------------------------------------
    fun name(): ObjectName {
        return objectLocation.objectPath.name
    }
}