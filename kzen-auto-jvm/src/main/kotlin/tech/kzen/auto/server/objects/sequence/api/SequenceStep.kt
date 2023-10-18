package tech.kzen.auto.server.objects.sequence.api

import tech.kzen.auto.server.objects.sequence.model.StepContext
import tech.kzen.auto.server.service.v1.model.LogicResult
import tech.kzen.auto.server.service.v1.model.tuple.TupleDefinition

/**
 * NB: new instance created every step when paused, use StatefulLogicElement to maintain state
 */
interface SequenceStep {
    // TODO: add support for definition context (eg. MultiStep to have value of last step)
    fun valueDefinition(): TupleDefinition

    fun continueOrStart(stepContext: StepContext): LogicResult
}