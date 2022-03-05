package tech.kzen.auto.server.objects.sequence.api

import tech.kzen.auto.server.objects.sequence.model.StepContext
import tech.kzen.auto.server.service.v1.model.LogicResult
import tech.kzen.auto.server.service.v1.model.TupleDefinition


interface SequenceStep {
    fun valueDefinition(): TupleDefinition
    fun continueOrStart(stepContext: StepContext): LogicResult
}