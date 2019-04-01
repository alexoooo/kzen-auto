package tech.kzen.auto.common.objects.document

import tech.kzen.auto.common.paradigm.imperative.ExecutionAction
import tech.kzen.lib.common.api.model.AttributePath
import tech.kzen.lib.common.api.model.ObjectLocation


@Suppress("unused")
class ScriptDocument(
        val steps: List<ExecutionAction>,
        objectLocation: ObjectLocation
):
        DocumentArchetype(objectLocation)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val stepsAttributePath = AttributePath.parse("steps")
    }
}
