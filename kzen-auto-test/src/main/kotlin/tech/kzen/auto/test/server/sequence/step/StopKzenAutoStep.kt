package tech.kzen.auto.test.server.sequence.step

import tech.kzen.auto.server.objects.sequence.api.SequenceStepDefinition
import tech.kzen.auto.server.objects.sequence.api.TracingSequenceStep
import tech.kzen.auto.server.objects.sequence.model.SequenceDefinitionContext
import tech.kzen.auto.server.objects.sequence.model.SequenceExecutionContext
import tech.kzen.auto.server.service.v1.model.LogicResult
import tech.kzen.auto.server.service.v1.model.LogicResultSuccess
import tech.kzen.auto.server.service.v1.model.tuple.TupleValue
import tech.kzen.auto.test.server.process.FixtureCopier
import tech.kzen.auto.test.server.process.KzenAutoSubprocessRegistry
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class StopKzenAutoStep(
    private val name: String,
    selfLocation: ObjectLocation
):
    TracingSequenceStep(selfLocation)
{
    override fun definition(sequenceDefinitionContext: SequenceDefinitionContext): SequenceStepDefinition {
        return SequenceStepDefinition.empty
    }


    override fun continueOrStart(
        sequenceExecutionContext: SequenceExecutionContext
    ): LogicResult {
        val entry = KzenAutoSubprocessRegistry.remove(name)
        if (entry == null) {
            traceDetail(
                sequenceExecutionContext,
                "no SUT registered as '$name', nothing to stop")
            return LogicResultSuccess(TupleValue.empty)
        }

        entry.process.close()
        entry.tempDir?.let { FixtureCopier.deleteRecursively(it) }

        traceDetail(sequenceExecutionContext, "stopped '$name'")
        return LogicResultSuccess(TupleValue.empty)
    }
}
