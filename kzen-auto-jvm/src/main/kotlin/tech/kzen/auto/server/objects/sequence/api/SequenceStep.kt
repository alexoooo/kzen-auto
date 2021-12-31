package tech.kzen.auto.server.objects.sequence.api

import tech.kzen.auto.server.objects.sequence.model.ActiveSequenceModel
import tech.kzen.auto.server.objects.sequence.model.StepValue


interface SequenceStep<T> {
    fun perform(
        activeSequenceModel: ActiveSequenceModel,
//        graphInstance: GraphInstance
    ): StepValue<T>
}