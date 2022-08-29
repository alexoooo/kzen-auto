package tech.kzen.auto.client.objects.document.script.step

import kotlinx.css.em
import react.*
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.document.script.step.display.StepDisplayPropsCommon
import tech.kzen.auto.client.objects.document.script.step.display.StepDisplayWrapper
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

        override fun child(input: RBuilder, handler: RHandler<StepControllerProps>)/*: ReactElement*/ {
            input.child(StepController::class) {
                attrs {
                    this.stepDisplays = this@Wrapper.stepDisplays
                }

                handler()
            }
        }
    }


    /**
     * NB: lazy reference to avoid loop
     */
    @Reflect
    class Handle {
        var wrapper: Wrapper? = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
//        +">> ${props.objectLocation.asString()}"

        val displayWrapperName = ObjectName(
                props.common.clientState.graphStructure().graphNotation.getString(
                        props.common.objectLocation, AutoConventions.displayAttributePath))

        val displayWrapper = props.stepDisplays.find { it.name() == displayWrapperName }
                ?: throw IllegalStateException("Step display not found: $displayWrapperName")

        displayWrapper.child(this) {
            attrs {
                common = props.common
            }
        }
    }
}