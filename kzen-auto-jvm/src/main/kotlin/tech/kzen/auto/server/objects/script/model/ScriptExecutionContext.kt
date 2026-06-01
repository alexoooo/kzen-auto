package tech.kzen.auto.server.objects.script.model

import tech.kzen.auto.common.objects.document.script.model.ScriptTree
import tech.kzen.auto.common.objects.document.script.model.ScriptValidation
import tech.kzen.lib.common.exec.logic.trace.LogicTraceHandle
import tech.kzen.lib.common.exec.logic.LogicControl
import tech.kzen.lib.common.exec.logic.LogicHandleFacade
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper


data class ScriptExecutionContext(
    val logicControl: LogicControl,
    val activeScriptModel: ActiveScriptModel,
    val logicHandleFacade: LogicHandleFacade,
    val logicTraceHandle: LogicTraceHandle,
    val graphInstance: GraphInstance,
    // Full (live) definition this pass is running against — passed into nested RunStep executions
    // so they resolve against current notation rather than a stale start-time snapshot.
    val graphDefinition: GraphDefinition,
    val arguments: TupleValue,
    val scriptTree: ScriptTree,
    val scriptValidation: ScriptValidation,
    val objectStableMapper: ObjectStableMapper
) {
    fun stepModel(objectLocation: ObjectLocation): ActiveStepModel? {
        return activeScriptModel.steps[objectStableMapper.objectStableId(objectLocation)]
    }

    fun getOrPutStepModel(objectLocation: ObjectLocation): ActiveStepModel {
        return activeScriptModel.steps.getOrPut(
            objectStableMapper.objectStableId(objectLocation)
        ) { ActiveStepModel() }
    }
}