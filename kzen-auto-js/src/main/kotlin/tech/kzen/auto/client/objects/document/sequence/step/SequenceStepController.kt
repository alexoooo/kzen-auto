package tech.kzen.auto.client.objects.document.sequence.step

import kotlinx.css.em
import react.RBuilder
import react.RHandler
import react.RPureComponent
import react.State
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.document.sequence.step.display.SequenceStepDisplayProps
import tech.kzen.auto.client.objects.document.sequence.step.display.SequenceStepDisplayWrapper
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.reflect.Reflect


class SequenceStepController(
        props: Props
):
        RPureComponent<SequenceStepController.Props, State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val width = 26.em
    }


    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var stepDisplays: List<SequenceStepDisplayWrapper>,

            var common: SequenceStepDisplayProps.Common
    ): react.Props


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        private val stepDisplays: List<SequenceStepDisplayWrapper>,
        handle: Handle
    ):
        ReactWrapper<Props>
    {
        init {
            handle.wrapper = this
        }

        override fun child(input: RBuilder, handler: RHandler<Props>) {
            input.child(SequenceStepController::class) {
                attrs {
                    this.stepDisplays = this@Wrapper.stepDisplays
                }

                handler()
            }
        }
    }


    /**
     * NB: lazy reference to avoid loop with nested steps
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