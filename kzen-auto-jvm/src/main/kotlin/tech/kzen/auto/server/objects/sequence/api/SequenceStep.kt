package tech.kzen.auto.server.objects.sequence.api

import tech.kzen.auto.server.objects.sequence.model.StepContext
import tech.kzen.auto.server.service.v1.model.LogicResult

/**
 * NB: new instance created every step when paused, use StatefulLogicElement to maintain state
 */
interface SequenceStep {
    fun definition(): SequenceStepDefinition

    fun continueOrStart(stepContext: StepContext): LogicResult
}