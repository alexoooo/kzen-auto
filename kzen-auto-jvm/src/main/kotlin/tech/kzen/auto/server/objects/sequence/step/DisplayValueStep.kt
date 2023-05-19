package tech.kzen.auto.server.objects.sequence.step

import tech.kzen.auto.common.paradigm.common.model.TextExecutionValue
import tech.kzen.auto.server.objects.sequence.api.TracingSequenceStep
import tech.kzen.auto.server.objects.sequence.model.StepContext
import tech.kzen.auto.server.service.v1.model.LogicResult
import tech.kzen.auto.server.service.v1.model.LogicResultSuccess
import tech.kzen.auto.server.service.v1.model.tuple.TupleComponentName
import tech.kzen.auto.server.service.v1.model.tuple.TupleComponentValue
import tech.kzen.auto.server.service.v1.model.tuple.TupleDefinition
import tech.kzen.auto.server.service.v1.model.tuple.TupleValue
import tech.kzen.lib.common.model.locate.ObjectLocation
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
        val value = step?.value

        val mainValue =
            if (value is List<*>) {
                val components = value.filterIsInstance<TupleComponentValue>()
                components.find { it.name == TupleComponentName.main }?.value
            }
            else {
                null
            }

        val executionValue =
            if (mainValue != null) {
                TextExecutionValue(mainValue.toString())
            }
            else {
                TextExecutionValue(value.toString())
            }

        traceDetail(stepContext, executionValue)

        return LogicResultSuccess(TupleValue.empty)
    }
}