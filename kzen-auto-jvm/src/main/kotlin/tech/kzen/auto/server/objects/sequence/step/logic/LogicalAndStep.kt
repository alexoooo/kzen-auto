package tech.kzen.auto.server.objects.sequence.step.logic

import tech.kzen.auto.server.objects.sequence.api.SequenceStepDefinition
import tech.kzen.auto.server.objects.sequence.api.TracingSequenceStep
import tech.kzen.auto.server.objects.sequence.model.SequenceDefinitionContext
import tech.kzen.auto.server.objects.sequence.model.SequenceExecutionContext
import tech.kzen.auto.server.service.v1.model.LogicResult
import tech.kzen.auto.server.service.v1.model.LogicResultSuccess
import tech.kzen.auto.server.service.v1.model.LogicType
import tech.kzen.auto.server.service.v1.model.tuple.TupleDefinition
import tech.kzen.auto.server.service.v1.model.tuple.TupleValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class LogicalAndStep(
    private val condition: ObjectLocation,
    private val and: ObjectLocation,
    selfLocation: ObjectLocation
):
    TracingSequenceStep(selfLocation)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(sequenceDefinitionContext: SequenceDefinitionContext): SequenceStepDefinition {
        return SequenceStepDefinition.of(
            TupleDefinition.ofMain(LogicType.boolean))
    }


    override fun continueOrStart(sequenceExecutionContext: SequenceExecutionContext): LogicResult {
        val conditionStep = sequenceExecutionContext.activeSequenceModel.steps[condition]
        val conditionValue = conditionStep?.value?.mainComponentValue()
        check(conditionValue is Boolean) {
            "Boolean expected: $condition = $conditionValue"
        }

        val andStep = sequenceExecutionContext.activeSequenceModel.steps[and]
        val andValue = andStep?.value?.mainComponentValue()
        check(andValue is Boolean) {
            "Boolean expected: $and = $andValue"
        }

        val result = conditionValue && andValue

        traceDetail(sequenceExecutionContext, result)

        return LogicResultSuccess(
            TupleValue.ofMain(result))
    }
}