package tech.kzen.auto.client.objects.document.script.step

//import kotlinx.css.em
import csstype.em
import react.ChildrenBuilder
import react.Props
import react.State
import react.react
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.document.script.step.display.StepDisplayPropsCommon
import tech.kzen.auto.client.objects.document.script.step.display.StepDisplayWrapper
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.reflect.Reflect




//---------------------------------------------------------------------------------------------------------------------
external interface StepControllerProps: Props {
    var stepDisplays: List<StepDisplayWrapper>
    var common: StepDisplayPropsCommon
}


//---------------------------------------------------------------------------------------------------------------------
class StepController(
        props: StepControllerProps
):
        RPureComponent<StepControllerProps, State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val width = 26.em
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
            private val stepDisplays: List<StepDisplayWrapper>,
            handle: Handle
    ):
            ReactWrapper<StepControllerProps>
    {
        init {
            handle.wrapper = this
        }

        override fun ChildrenBuilder.child(block: StepControllerProps.() -> Unit) {
            StepController::class.react {
                stepDisplays = this@Wrapper.stepDisplays
                block()
            }
        }
    }


    /**
     * NB: lazy reference to avoid cycle when looking up the StepController from the StepDisplay
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