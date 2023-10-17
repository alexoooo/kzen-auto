package tech.kzen.auto.client.objects.document.sequence.step.control.mapping

import react.ChildrenBuilder
import react.State
import react.react
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.document.sequence.command.SequenceCommander
import tech.kzen.auto.client.objects.document.sequence.display.SequenceStepDisplayProps
import tech.kzen.auto.client.objects.document.sequence.display.SequenceStepDisplayWrapper
import tech.kzen.auto.client.objects.document.sequence.display.StepDisplayManager
import tech.kzen.auto.client.objects.document.sequence.model.SequenceState
import tech.kzen.auto.client.objects.document.sequence.model.SequenceStore
import tech.kzen.auto.client.service.global.SessionGlobal
import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.client.wrap.RComponent
import tech.kzen.auto.common.paradigm.sequence.StepTrace
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


//---------------------------------------------------------------------------------------------------------------------
external interface MappingStepDisplayProps: SequenceStepDisplayProps {
    var attributeEditorManager: AttributeEditorManager.Wrapper
    var stepDisplayManager: StepDisplayManager.Wrapper
    var sequenceCommander: SequenceCommander
}


external interface MappingStepDisplayState: State {
    var stepTrace: StepTrace?
    var isNextToRun: Boolean?

    var icon: String?
    var description: String?
    var title: String?
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class MappingStepDisplay(
    props: MappingStepDisplayProps
):
    RComponent<MappingStepDisplayProps, MappingStepDisplayState>(props),
    SessionGlobal.Observer,
    SequenceStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val itemsAttributeName = AttributeName("items")

//        val stepsAttributeName = AttributeName("steps")
//        val stepsAttributePath = AttributePath.ofName(stepsAttributeName)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation,
        private val attributeEditorManager: AttributeEditorManager.Wrapper,
        private val stepDisplayManager: StepDisplayManager.Handle,
        private val sequenceCommander: SequenceCommander
    ):
        SequenceStepDisplayWrapper(objectLocation)
    {
        override fun ChildrenBuilder.child(block: SequenceStepDisplayProps.() -> Unit) {
            MappingStepDisplay::class.react {
                attributeEditorManager = this@Wrapper.attributeEditorManager
                stepDisplayManager = this@Wrapper.stepDisplayManager.wrapper!!
                sequenceCommander = this@Wrapper.sequenceCommander

                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onSequenceState(sequenceState: SequenceState) {

    }

    override fun onClientState(clientState: SessionState) {

    }

    override fun ChildrenBuilder.render() {
        +"[MappingStepDisplay]"
    }
}