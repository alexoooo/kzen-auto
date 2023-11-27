package tech.kzen.auto.server.objects.sequence.step.control

import org.slf4j.LoggerFactory
import tech.kzen.auto.server.objects.sequence.api.SequenceStepDefinition
import tech.kzen.auto.server.objects.sequence.api.TracingSequenceStep
import tech.kzen.auto.server.objects.sequence.model.SequenceDefinitionContext
import tech.kzen.auto.server.objects.sequence.model.SequenceExecutionContext
import tech.kzen.auto.server.service.v1.model.LogicResult
import tech.kzen.auto.server.service.v1.model.LogicResultSuccess
import tech.kzen.auto.server.service.v1.model.tuple.TupleValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class WaitStep(
    private val milliseconds: Long,
    private val selfLocation: ObjectLocation
):
    TracingSequenceStep(selfLocation)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(WaitStep::class.java)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(sequenceDefinitionContext: SequenceDefinitionContext): SequenceStepDefinition {
        return SequenceStepDefinition.empty
    }


    override fun continueOrStart(
        sequenceExecutionContext: SequenceExecutionContext
    ): LogicResult {
        logger.info("{} - milliseconds = {}", selfLocation, milliseconds)

        traceDetail(sequenceExecutionContext, "Waiting for $milliseconds milliseconds")

        Thread.sleep(milliseconds)

        traceDetail(sequenceExecutionContext, "Finished waiting for $milliseconds milliseconds")

        return LogicResultSuccess(TupleValue.empty)
    }
}