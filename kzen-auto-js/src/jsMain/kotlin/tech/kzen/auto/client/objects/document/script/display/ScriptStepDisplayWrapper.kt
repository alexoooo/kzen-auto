package tech.kzen.auto.client.objects.document.script.display

import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName


abstract class ScriptStepDisplayWrapper(
    private val objectLocation: ObjectLocation
):
    ReactWrapper<ScriptStepDisplayProps>
{
    //-----------------------------------------------------------------------------------------------------------------
    fun name(): ObjectName {
        return objectLocation.objectPath.name
    }
}