package tech.kzen.auto.server.objects.sequence.step.control.mapping

import tech.kzen.auto.server.objects.sequence.api.SequenceStep
import tech.kzen.auto.server.objects.sequence.model.StepContext
import tech.kzen.auto.server.service.v1.model.LogicResult
import tech.kzen.auto.server.service.v1.model.LogicResultFailed
import tech.kzen.auto.server.service.v1.model.LogicResultSuccess
import tech.kzen.auto.server.service.v1.model.LogicType
import tech.kzen.auto.server.service.v1.model.tuple.TupleDefinition
import tech.kzen.auto.server.service.v1.model.tuple.TupleValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class MappingItemStep(
    private val selfLocation: ObjectLocation
): SequenceStep {
    override fun valueDefinition(): TupleDefinition {
        return TupleDefinition.ofMain(LogicType.any)
    }


    override fun continueOrStart(stepContext: StepContext): LogicResult {
        val parentLocation = selfLocation.parent()
            ?: return LogicResultFailed("Parent location not found")

        val parentMapping = stepContext.graphInstance[parentLocation]!!.reference as? MappingStep
            ?: return LogicResultFailed("Parent mapping expected: $parentLocation")

        val next = parentMapping.next
            ?: return LogicResultFailed("Next mapping not found: $parentLocation")

        return LogicResultSuccess(
            TupleValue.ofMain(next))
    }
}