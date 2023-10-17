package tech.kzen.auto.server.objects.sequence.step.value

import tech.kzen.auto.common.paradigm.common.model.TextExecutionValue
import tech.kzen.auto.server.objects.sequence.api.TracingSequenceStep
import tech.kzen.auto.server.objects.sequence.model.StepContext
import tech.kzen.auto.server.service.v1.model.LogicResult
import tech.kzen.auto.server.service.v1.model.LogicResultSuccess
import tech.kzen.auto.server.service.v1.model.tuple.TupleDefinition
import tech.kzen.auto.server.service.v1.model.tuple.TupleValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class DisplayValueStep(
    private val text: ObjectLocation,
    selfLocation: ObjectLocation
):
    TracingSequenceStep(selfLocation)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun valueDefinition(): TupleDefinition {
        return TupleDefinition.empty
    }


    override fun continueOrStart(stepContext: StepContext): LogicResult {
        val step = stepContext.activeSequenceModel.steps[text]
        val value = step?.value?.mainComponentValue()

        val executionValue = TextExecutionValue(value?.toString() ?: "<null>")

        traceDetail(stepContext, executionValue)

        return LogicResultSuccess(TupleValue.empty)
    }
}