package tech.kzen.auto.client.objects.document.sequence.step.control

import react.ChildrenBuilder
import react.State
import react.react
import tech.kzen.auto.client.objects.document.sequence.command.SequenceCommander
import tech.kzen.auto.client.objects.document.sequence.display.SequenceBranchDisplay
import tech.kzen.auto.client.objects.document.sequence.display.SequenceStepDisplayProps
import tech.kzen.auto.client.objects.document.sequence.display.SequenceStepDisplayWrapper
import tech.kzen.auto.client.objects.document.sequence.display.StepDisplayManager
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.common.objects.document.sequence.SequenceConventions
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


//---------------------------------------------------------------------------------------------------------------------
external interface MultiStepDisplayProps: SequenceStepDisplayProps {
    var stepDisplayManager: StepDisplayManager.Handle
    var sequenceCommander: SequenceCommander
}


//---------------------------------------------------------------------------------------------------------------------
class MultiStepDisplay(
    props: MultiStepDisplayProps
):
    RPureComponent<MultiStepDisplayProps, State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation,
        private val stepDisplayManager: StepDisplayManager.Handle,
        private val sequenceCommander: SequenceCommander
    ):
        SequenceStepDisplayWrapper(objectLocation)
    {
        override fun ChildrenBuilder.child(block: SequenceStepDisplayProps.() -> Unit) {
            MultiStepDisplay::class.react {
                stepDisplayManager = this@Wrapper.stepDisplayManager
                sequenceCommander = this@Wrapper.sequenceCommander
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val stepDisplayManager = props.stepDisplayManager.wrapper
            ?: return

        SequenceBranchDisplay::class.react {
            attributeLocation = AttributeLocation(
                props.common.objectLocation,
                SequenceConventions.stepsAttributePath)

            this.stepDisplayManager = stepDisplayManager
            sequenceCommander = props.sequenceCommander
        }
    }
}