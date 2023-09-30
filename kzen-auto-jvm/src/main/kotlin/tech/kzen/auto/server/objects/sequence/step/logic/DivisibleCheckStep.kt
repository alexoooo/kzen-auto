package tech.kzen.auto.server.objects.sequence.step.logic

import tech.kzen.auto.server.objects.sequence.api.TracingSequenceStep
import tech.kzen.auto.server.objects.sequence.model.StepContext
import tech.kzen.auto.server.service.v1.model.LogicResult
import tech.kzen.auto.server.service.v1.model.LogicResultSuccess
import tech.kzen.auto.server.service.v1.model.LogicType
import tech.kzen.auto.server.service.v1.model.tuple.TupleDefinition
import tech.kzen.auto.server.service.v1.model.tuple.TupleValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class DivisibleCheckStep(
    private val number: ObjectLocation,
    private val divisor: Int,
    selfLocation: ObjectLocation
):
    TracingSequenceStep(selfLocation)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun valueDefinition(): TupleDefinition {
        return TupleDefinition.ofMain(LogicType.boolean)
    }


    override fun continueOrStart(stepContext: StepContext): LogicResult {
        val step = stepContext.activeSequenceModel.steps[number]

        val value = step?.value?.mainComponentValue()
        check(value is Number) {
            "Number expected: $number = $value"
        }

        val intValue = value.toInt()
        check(intValue.toDouble() == value.toDouble()) {
            "Integer expected: $number = $value"
        }

        require(divisor > 0) {
            "Positive divisor required: $divisor"
        }

        val result = intValue % divisor == 0

        traceDetail(stepContext, result)

        return LogicResultSuccess(
            TupleValue.ofMain(result))
    }
}