package tech.kzen.auto.common.objects.document.script

import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.paradigm.imperative.api.ExecutionAction
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.locate.ObjectLocation


@Suppress("unused")
class ScriptDocument(
        val steps: List<ExecutionAction>,
        objectLocation: ObjectLocation
):
        DocumentArchetype(objectLocation)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val stepsAttributeName = AttributeName("steps")
        val stepsAttributePath = AttributePath.ofName(stepsAttributeName)
    }
}
