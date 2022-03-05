package tech.kzen.auto.server.objects.sequence.step

import tech.kzen.auto.server.objects.sequence.api.SequenceStep
import tech.kzen.auto.server.objects.sequence.model.StepContext
import tech.kzen.auto.server.service.v1.model.*
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class InvokeSequenceStep(
    private val sequence: ObjectLocation
):
    SequenceStep
{
//    companion object {
//        private val logger = LoggerFactory.getLogger(InvokeSequenceStep::class.java)
//    }


    override fun valueDefinition(): TupleDefinition {
        return TupleDefinition.ofMain(LogicType.any)
    }

    override fun continueOrStart(stepContext: StepContext): LogicResult {
//        TODO("Not yet implemented")
//    }
//
//
//    override fun perform(
//        activeSequenceModel: ActiveSequenceModel,
//        logicHandleFacade: LogicHandleFacade
//    ): StepValue<Any> {
        val execution = stepContext.logicHandleFacade.start(sequence)

        val resultValue = execution.use { closingExecution ->
            val initResult = closingExecution.beforeStart(TupleValue.empty)
            check(initResult)

            val runResult = closingExecution.continueOrStart()
                    as LogicResultSuccess

            runResult.value
        }

        return LogicResultSuccess(
            TupleValue.ofMain(resultValue))
    }
}