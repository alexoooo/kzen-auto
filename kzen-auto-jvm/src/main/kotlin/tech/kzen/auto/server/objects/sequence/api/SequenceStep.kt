package tech.kzen.auto.server.objects.sequence.api

import tech.kzen.auto.server.objects.sequence.model.ActiveSequenceModel
import tech.kzen.auto.server.objects.sequence.model.StepValue
import tech.kzen.auto.server.service.v1.LogicHandle


interface SequenceStep<T> {
    fun perform(
        activeSequenceModel: ActiveSequenceModel,
        logicHandle: LogicHandle
//        graphInstance: GraphInstance
    ): StepValue<T>
}