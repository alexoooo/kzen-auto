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
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.platform.ClassNames


@Reflect
class NumberRangeStep(
    private val from: Int,
    private val to: Int,
    private val selfLocation: ObjectLocation
):
    TracingSequenceStep(selfLocation)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(NumberRangeStep::class.java)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(sequenceDefinitionContext: SequenceDefinitionContext): SequenceStepDefinition {
        return SequenceStepDefinition.of(TupleDefinition.ofMain(
            LogicType(TypeMetadata(ClassNames.kotlinList, listOf(TypeMetadata.int), false))))
    }


    override fun continueOrStart(
        sequenceExecutionContext: SequenceExecutionContext
    ): LogicResult {
        logger.info("{} - from = {} | to = {}", selfLocation, from, to)

        traceValue(sequenceExecutionContext, "[$from .. $to]")

        val items = mutableListOf<Int>()
        for (n in from .. to) {
            items.add(n)
        }

        return LogicResultSuccess(
            TupleValue.ofMain(items))
    }
}