package tech.kzen.auto.server.objects.sequence.step.value

import org.slf4j.LoggerFactory
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
class BooleanLiteralStep(
    private val value: Boolean,
    private val selfLocation: ObjectLocation
):
    TracingSequenceStep(selfLocation)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(BooleanLiteralStep::class.java)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(sequenceDefinitionContext: SequenceDefinitionContext): SequenceStepDefinition {
        return SequenceStepDefinition.of(
            TupleDefinition.ofMain(LogicType.boolean))
    }


    override fun continueOrStart(
        sequenceExecutionContext: SequenceExecutionContext
    ): LogicResult {
        logger.info("{} - value = {}", selfLocation, value)

        traceValue(sequenceExecutionContext, value)

        return LogicResultSuccess(
            TupleValue.ofMain(value))
    }
}