package tech.kzen.auto.server.objects.sequence.api

import tech.kzen.auto.server.objects.sequence.model.ActiveSequenceModel
import tech.kzen.auto.server.objects.sequence.model.StepValue
import tech.kzen.auto.server.service.v1.LogicHandleFacade


interface SequenceStep<T> {
    fun perform(
        activeSequenceModel: ActiveSequenceModel,
        logicHandleFacade: LogicHandleFacade
//        graphInstance: GraphInstance
    ): StepValue<T>
}