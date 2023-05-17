package tech.kzen.auto.client.objects.document.sequence.step

import web.cssom.em
import react.ChildrenBuilder
import react.State
import react.react
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.document.sequence.step.display.SequenceStepDisplayPropsCommon
import tech.kzen.auto.client.objects.document.sequence.step.display.SequenceStepDisplayWrapper
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.reflect.Reflect


external interface SequenceStepControllerProps: react.Props {
    var stepDisplays: List<SequenceStepDisplayWrapper>

    var common: SequenceStepDisplayPropsCommon
}


class SequenceStepController(
        props: SequenceStepControllerProps
):
        RPureComponent<SequenceStepControllerProps, State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val width = 26.em
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        private val stepDisplays: List<SequenceStepDisplayWrapper>,
        handle: Handle
    ):
        ReactWrapper<SequenceStepControllerProps>
    {
        init {
            handle.wrapper = this
        }

        override fun ChildrenBuilder.child(block: SequenceStepControllerProps.() -> Unit) {
            SequenceStepController::class.react {
                this.stepDisplays = this@Wrapper.stepDisplays
                block()
            }
        }
    }


    /**
     * NB: lazy reference to avoid reference cycle with nested steps
     */
    @Reflect
    class Handle {
        var wrapper: Wrapper? = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
//        +">> ${props.objectLocation.asString()}"

        val displayWrapperName = ObjectName(
                props.common.clientState.graphStructure().graphNotation.getString(
                        props.common.objectLocation, AutoConventions.displayAttributePath))

        val displayWrapper = props.stepDisplays.find { it.name() == displayWrapperName }
                ?: throw IllegalStateException("Step display not found: $displayWrapperName")

        displayWrapper.child(this) {
            common = props.common
        }
    }
}