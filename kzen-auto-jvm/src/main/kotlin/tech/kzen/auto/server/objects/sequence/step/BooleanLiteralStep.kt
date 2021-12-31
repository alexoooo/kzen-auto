package tech.kzen.auto.server.objects.sequence.step

import org.slf4j.LoggerFactory
import tech.kzen.auto.server.objects.sequence.api.SequenceStep
import tech.kzen.auto.server.objects.sequence.model.ActiveSequenceModel
import tech.kzen.auto.server.objects.sequence.model.StepValue
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class BooleanLiteralStep(
    private val value: Boolean
):
    SequenceStep<Boolean>
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(BooleanLiteralStep::class.java)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun perform(
        activeSequenceModel: ActiveSequenceModel,
//        graphInstance: GraphInstance
    ): StepValue<Boolean> {
        logger.info("value = {}", value)
        return StepValue.ofValue(value)
    }
}