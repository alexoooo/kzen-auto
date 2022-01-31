package tech.kzen.auto.server.objects.sequence.step

import tech.kzen.auto.common.paradigm.common.model.NullExecutionValue
import tech.kzen.auto.server.objects.sequence.api.SequenceStep
import tech.kzen.auto.server.objects.sequence.model.ActiveSequenceModel
import tech.kzen.auto.server.objects.sequence.model.StepValue
import tech.kzen.auto.server.service.v1.LogicHandleFacade
import tech.kzen.auto.server.service.v1.model.LogicResultSuccess
import tech.kzen.auto.server.service.v1.model.TupleValue
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class InvokeSequenceStep(
    private val sequence: ObjectLocation
):
    SequenceStep<Any>
{
//    companion object {
//        private val logger = LoggerFactory.getLogger(InvokeSequenceStep::class.java)
//    }


    override fun perform(
        activeSequenceModel: ActiveSequenceModel,
        logicHandleFacade: LogicHandleFacade
    ): StepValue<Any> {
        val execution = logicHandleFacade.start(sequence)

        val resultValue = execution.use { closingExecution ->
            val initResult = closingExecution.beforeStart(TupleValue.empty)
            check(initResult)

            val runResult = closingExecution.continueOrStart()
                    as LogicResultSuccess

            runResult.value
        }

        return StepValue(resultValue, NullExecutionValue)
    }
}