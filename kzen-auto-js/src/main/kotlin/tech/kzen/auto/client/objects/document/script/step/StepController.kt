package tech.kzen.auto.client.objects.document.script.step

import react.*
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.document.script.step.display.StepDisplayWrapper
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeState
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.structure.GraphStructure


class StepController(
        props: Props
):
        RPureComponent<StepController.Props, RState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val displayAttributePath = AttributePath.ofName(AttributeName("display"))
    }


    class Props(
            var stepDisplays: List<StepDisplayWrapper>,

            var graphStructure: GraphStructure,
            var objectLocation: ObjectLocation,
            var attributeNesting: AttributeNesting,
            var imperativeState: ImperativeState?
    ): RProps


    @Suppress("unused")
    class Wrapper(
            private val stepDisplays: List<StepDisplayWrapper>
    ):
            ReactWrapper<Props>
    {
        override fun child(input: RBuilder, handler: RHandler<Props>): ReactElement {
            return input.child(StepController::class) {
                attrs {
                    this.stepDisplays = this@Wrapper.stepDisplays
                }

                handler()
            }
        }
    }

    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val displayWrapperName = ObjectName(
                props.graphStructure.graphNotation.getString(props.objectLocation, displayAttributePath))

        val displayWrapper = props.stepDisplays.find { it.name() == displayWrapperName }
                ?: throw IllegalStateException("Step display not found: $displayWrapperName")

        displayWrapper.child(this) {
            attrs {
                graphStructure = props.graphStructure
                objectLocation = props.objectLocation
                attributeNesting = props.attributeNesting
                imperativeState = props.imperativeState
            }
        }
    }
}