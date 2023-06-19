package tech.kzen.auto.server.objects.sequence.model

import tech.kzen.auto.server.objects.logic.LogicTraceHandle
import tech.kzen.auto.server.service.v1.LogicControl
import tech.kzen.auto.server.service.v1.LogicHandleFacade
import tech.kzen.lib.common.model.instance.GraphInstance


data class StepContext(
    val logicControl: LogicControl,
    val activeSequenceModel: ActiveSequenceModel,
    val logicHandleFacade: LogicHandleFacade,
    val logicTraceHandle: LogicTraceHandle,
    val graphInstance: GraphInstance,
//    val topLevel: Boolean
)