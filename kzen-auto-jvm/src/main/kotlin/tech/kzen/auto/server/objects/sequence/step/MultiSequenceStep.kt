package tech.kzen.auto.server.objects.sequence.step

import tech.kzen.auto.server.objects.sequence.api.SequenceStep
import tech.kzen.auto.server.objects.sequence.model.ActiveStepModel
import tech.kzen.auto.server.objects.sequence.model.StepContext
import tech.kzen.auto.server.service.v1.model.LogicResult
import tech.kzen.auto.server.service.v1.model.LogicResultSuccess
import tech.kzen.auto.server.service.v1.model.TupleDefinition
import tech.kzen.auto.server.service.v1.model.TupleValue
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class MultiSequenceStep(
    private val steps: List<ObjectLocation>
):
    SequenceStep
{
    override fun valueDefinition(): TupleDefinition {
        return TupleDefinition.empty
    }


    override fun continueOrStart(stepContext: StepContext): LogicResult {
        for (stepLocation in steps) {
            val step = stepContext.graphInstance[stepLocation]!!.reference as SequenceStep
            val model = stepContext.activeSequenceModel.steps.getOrPut(stepLocation) { ActiveStepModel() }

            val result = step.continueOrStart(stepContext)
            if (result is LogicResultSuccess) {
                model.value = result.value.components
            }
        }

        return LogicResultSuccess(TupleValue.empty)
    }
}