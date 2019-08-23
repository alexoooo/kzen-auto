package tech.kzen.auto.client.objects.document.script.step

import react.*
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.document.script.step.display.StepDisplayWrapper
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.structure.GraphStructure


class StepController(
        props: Props
):
        RPureComponent<StepController.Props, RState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var stepDisplays: List<StepDisplayWrapper>,

            var graphStructure: GraphStructure,
            var objectLocation: ObjectLocation,
            var attributeNesting: AttributeNesting,
            var imperativeModel: ImperativeModel
    ): RProps


    @Suppress("unused")
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
    class Handle {
        var wrapper: Wrapper? = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
//        +">> ${props.objectLocation.asString()}"

        val displayWrapperName = ObjectName(
                props.graphStructure.graphNotation.getString(
                        props.objectLocation, AutoConventions.displayAttributePath))

        val displayWrapper = props.stepDisplays.find { it.name() == displayWrapperName }
                ?: throw IllegalStateException("Step display not found: $displayWrapperName")

        displayWrapper.child(this) {
            attrs {
                graphStructure = props.graphStructure
                objectLocation = props.objectLocation
                attributeNesting = props.attributeNesting
                imperativeModel = props.imperativeModel
            }
        }
    }
}