package tech.kzen.auto.server.objects.sequence.model

import tech.kzen.auto.common.objects.document.sequence.model.SequenceTree
import tech.kzen.auto.common.objects.document.sequence.model.SequenceValidation
import tech.kzen.auto.server.objects.logic.LogicTraceHandle
import tech.kzen.auto.server.service.v1.LogicControl
import tech.kzen.auto.server.service.v1.LogicHandleFacade
import tech.kzen.auto.server.service.v1.model.tuple.TupleValue
import tech.kzen.lib.common.model.instance.GraphInstance


data class SequenceExecutionContext(
    val logicControl: LogicControl,
    val activeSequenceModel: ActiveSequenceModel,
    val logicHandleFacade: LogicHandleFacade,
    val logicTraceHandle: LogicTraceHandle,
    val graphInstance: GraphInstance,
    val arguments: TupleValue,
    val sequenceTree: SequenceTree,
    val sequenceValidation: SequenceValidation
//    val topLevel: Boolean
)