package tech.kzen.auto.client.objects.document.script.step

import kotlinx.css.em
import react.*
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.document.script.step.display.StepDisplayProps
import tech.kzen.auto.client.objects.document.script.step.display.StepDisplayWrapper
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.reflect.Reflect


class StepController(
        props: Props
):
        RPureComponent<StepController.Props, RState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val width = 26.em
    }


    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var stepDisplays: List<StepDisplayWrapper>,

            var common: StepDisplayProps.Common
    ): RProps


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
            private val stepDisplays: List<StepDisplayWrapper>,
            handle: Handle
    ):
            ReactWrapper<Props>
    {
        init {
            handle.wrapper = this
        }

        override fun child(input: RBuilder, handler: RHandler<Props>): ReactElement {
            return input.child(StepController::class) {
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