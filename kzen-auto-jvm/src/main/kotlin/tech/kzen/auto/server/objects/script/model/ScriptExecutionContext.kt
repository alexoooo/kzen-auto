package tech.kzen.auto.server.objects.script.model

import tech.kzen.auto.common.objects.document.script.model.ScriptTree
import tech.kzen.auto.common.objects.document.script.model.ScriptValidation
import tech.kzen.auto.server.objects.logic.LogicTraceHandle
import tech.kzen.auto.server.service.v1.LogicControl
import tech.kzen.auto.server.service.v1.LogicHandleFacade
import tech.kzen.auto.server.service.v1.model.tuple.TupleValue
import tech.kzen.lib.common.model.instance.GraphInstance


data class ScriptExecutionContext(
    val logicControl: LogicControl,
    val activeScriptModel: ActiveScriptModel,
    val logicHandleFacade: LogicHandleFacade,
    val logicTraceHandle: LogicTraceHandle,
    val graphInstance: GraphInstance,
    val arguments: TupleValue,
    val scriptTree: ScriptTree,
    val scriptValidation: ScriptValidation
//    val topLevel: Boolean
)