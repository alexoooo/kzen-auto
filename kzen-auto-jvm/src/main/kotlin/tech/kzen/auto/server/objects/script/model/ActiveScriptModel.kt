package tech.kzen.auto.server.objects.script.model

import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.store.normal.ObjectStableId
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper


data class ActiveScriptModel(
    val steps: MutableMap<ObjectStableId, ActiveStepModel> = mutableMapOf(),
    var next: ObjectStableId? = null
) {
    fun resetAll(prefix: ObjectLocation, objectStableMapper: ObjectStableMapper) {
        for (stepId in steps.keys) {
            val location = objectStableMapper.objectLocation(stepId)
            if (location.startsWith(prefix)) {
                steps[stepId]!!.reset()
            }
        }
    }
}