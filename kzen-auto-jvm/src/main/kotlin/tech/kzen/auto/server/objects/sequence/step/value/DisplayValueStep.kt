package tech.kzen.auto.server.objects.sequence.step.value

import tech.kzen.auto.server.objects.sequence.api.SequenceStepDefinition
import tech.kzen.auto.server.objects.sequence.api.TracingSequenceStep
import tech.kzen.auto.server.objects.sequence.model.SequenceDefinitionContext
import tech.kzen.auto.server.objects.sequence.model.SequenceExecutionContext
import tech.kzen.auto.server.service.v1.model.LogicResult
import tech.kzen.auto.server.service.v1.model.LogicResultSuccess
import tech.kzen.auto.server.service.v1.model.tuple.TupleValue
import tech.kzen.lib.common.exec.TextExecutionValue
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
    override fun definition(sequenceDefinitionContext: SequenceDefinitionContext): SequenceStepDefinition {
        return SequenceStepDefinition.empty
    }


    override fun continueOrStart(sequenceExecutionContext: SequenceExecutionContext): LogicResult {
        val step = sequenceExecutionContext.activeSequenceModel.steps[text]
        val value = step?.value?.mainComponentValue()

        val text = value?.toString() ?: "<null>"
        val executionValue = TextExecutionValue(text)

        traceDetail(sequenceExecutionContext, executionValue)

        return LogicResultSuccess(TupleValue.ofMain(text))
    }
}