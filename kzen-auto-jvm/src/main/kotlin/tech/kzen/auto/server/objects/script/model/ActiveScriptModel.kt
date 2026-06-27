package tech.kzen.auto.server.objects.script.model

import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.store.normal.ObjectStableId
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper


data class ActiveScriptModel(
    val steps: MutableMap<ObjectStableId, ActiveStepModel> = mutableMapOf(),
    var next: ObjectStableId? = null,

    // The last invoked Result step's value (VB-style last-wins), or null when no Result step has run.
    // Deliberately outside the per-prefix `steps` map so resetAll (loop/sub-script reset) preserves it
    // across iterations and pause/resume passes; ScriptDocument.continueOrStart reads it as the Script result.
    var resultValue: TupleValue? = null
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