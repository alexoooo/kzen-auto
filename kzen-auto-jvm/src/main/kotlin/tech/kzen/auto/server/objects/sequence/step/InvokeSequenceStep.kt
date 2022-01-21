package tech.kzen.auto.server.objects.sequence.step

import tech.kzen.auto.common.paradigm.common.model.NullExecutionValue
import tech.kzen.auto.server.objects.sequence.api.SequenceStep
import tech.kzen.auto.server.objects.sequence.model.ActiveSequenceModel
import tech.kzen.auto.server.objects.sequence.model.StepValue
import tech.kzen.auto.server.service.v1.LogicHandle
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
        logicHandle: LogicHandle
    ): StepValue<Any> {
        val execution = logicHandle.start(sequence)

        val initResult = execution.next(TupleValue.empty)
        initResult as LogicResultSuccess

        val runResult = execution.stepOrRun()
            as LogicResultSuccess

        val resultValue = runResult.value

        return StepValue(resultValue, NullExecutionValue)
    }
}