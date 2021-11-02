package tech.kzen.auto.server.objects.sequence.step

import tech.kzen.auto.server.objects.sequence.api.SequenceStep
import tech.kzen.auto.server.objects.sequence.model.ActiveSequenceModel
import tech.kzen.auto.server.objects.sequence.model.StepValue
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class BooleanLiteralStep(
    private val value: Boolean
):
    SequenceStep<Boolean>
{
    override fun perform(
        activeSequenceModel: ActiveSequenceModel,
        graphInstance: GraphInstance
    ): StepValue<Boolean> {
        return StepValue.ofValue(value)
    }
}