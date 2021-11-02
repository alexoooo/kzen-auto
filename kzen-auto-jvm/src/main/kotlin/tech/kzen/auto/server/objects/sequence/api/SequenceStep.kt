package tech.kzen.auto.server.objects.sequence.api

import tech.kzen.auto.server.objects.sequence.model.ActiveSequenceModel
import tech.kzen.auto.server.objects.sequence.model.StepValue
import tech.kzen.lib.common.model.instance.GraphInstance


interface SequenceStep<T> {
    fun perform(
        activeSequenceModel: ActiveSequenceModel,
        graphInstance: GraphInstance
    ): StepValue<T>
}