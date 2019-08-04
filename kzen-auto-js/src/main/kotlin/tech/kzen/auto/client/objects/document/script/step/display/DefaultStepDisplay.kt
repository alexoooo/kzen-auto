package tech.kzen.auto.client.objects.document.script.step.display

import kotlinx.css.*
import kotlinx.html.js.onMouseOutFunction
import kotlinx.html.js.onMouseOverFunction
import kotlinx.html.title
import react.RBuilder
import react.RHandler
import react.RState
import react.ReactElement
import react.dom.img
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.objects.document.common.AttributeController
import tech.kzen.auto.client.objects.document.script.step.header.StepHeader
import tech.kzen.auto.client.wrap.MaterialCardContent
import tech.kzen.auto.client.wrap.MaterialPaper
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.paradigm.common.model.BinaryExecutionValue
import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.auto.common.paradigm.common.model.ScalarExecutionValue
import tech.kzen.auto.common.paradigm.imperative.model.ImperativePhase
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeState
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeSuccess
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.metadata.model.ObjectMetadata
import tech.kzen.lib.platform.IoUtils


@Suppress("unused")
class DefaultStepDisplay(
        props: Props
):
        RPureComponent<DefaultStepDisplay.Props, DefaultStepDisplay.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
//    companion object {
//        val wrapperName = ObjectName("DefaultStepDisplay")
//    }


    class Props(
            var attributeController: AttributeController.Wrapper,

            graphStructure: GraphStructure,
            objectLocation: ObjectLocation,
            attributeNesting: AttributeNesting,
            imperativeState: ImperativeState?
    ): StepDisplayProps(
            graphStructure, objectLocation, attributeNesting, imperativeState
    )


    class State(
//            var hoverCard: Boolean
    ): RState


    @Suppress("unused")
    class Wrapper(
            objectLocation: ObjectLocation,
            private val attributeController: AttributeController.Wrapper
    ):
            StepDisplayWrapper(objectLocation)
    {
        override fun child(input: RBuilder, handler: RHandler<StepDisplayProps>): ReactElement {
            return input.child(DefaultStepDisplay::class) {
                attrs {
                    this.attributeController = this@Wrapper.attributeController
                }

                handler()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var hoverSignal = StepHeader.HoverSignal()


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
//        hoverCard = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onMouseOver() {
        hoverSignal.triggerMouseOver()
    }


    private fun onMouseOut() {
        hoverSignal.triggerMouseOut()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        styledSpan {
            css {
                width = 20.em
            }

            attrs {
                onMouseOverFunction = {
                    onMouseOver(/*true*/)
                }

                onMouseOutFunction = {
                    onMouseOut(/*true*/)
                }
            }

            renderCard()
        }
    }


    private fun RBuilder.renderCard() {
        val objectMetadata = props.graphStructure.graphMetadata.objectMetadata[props.objectLocation]!!

        val reactStyles = reactStyle {
            val cardColor = when (props.imperativeState?.phase()) {
                ImperativePhase.Pending ->
                    Color.white

                ImperativePhase.Running ->
                    Color.gold

                ImperativePhase.Success ->
                    Color("#00b467")

                ImperativePhase.Error ->
                    Color.red

                null ->
                    Color.gray
            }

            backgroundColor = cardColor
        }

        child(MaterialPaper::class) {
            attrs {
                style = reactStyles
            }

            child(MaterialCardContent::class) {
                child(StepHeader::class) {
                    attrs {
                        hoverSignal = this@DefaultStepDisplay.hoverSignal

                        attributeNesting = props.attributeNesting
                        objectLocation = props.objectLocation
                        graphStructure = props.graphStructure

                        imperativeState = props.imperativeState
                    }
                }

                styledDiv {
                    css {
                        marginBottom = (-1.5).em
                    }

                    renderBody(objectMetadata)
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderBody(objectMetadata: ObjectMetadata) {
        for (e in objectMetadata.attributes.values) {
            if (AutoConventions.isManaged(e.key)) {
                continue
            }

            styledDiv {
                css {
                    marginBottom = 0.5.em
                }

                renderAttribute(e.key)
            }
        }

        (props.imperativeState?.previous as? ImperativeSuccess)?.value?.let {
            renderValue(it)
        }

        (props.imperativeState?.previous as? ImperativeSuccess)?.detail?.let {
            renderDetail(it)
        }
    }


    private fun RBuilder.renderValue(value: ExecutionValue) {
        if (value is ScalarExecutionValue) {
            styledDiv {
                attrs {
                    title = "Result"
                }

                css {
                    padding(0.em, 0.5.em, 0.5.em, 0.5.em)
                }

                styledDiv {
                    css {
                        backgroundColor = Color("rgba(0, 0, 0, 0.04)")
                        padding(0.5.em)
                    }

                    +"${value.get()}"
                }
            }
        }
    }


    private fun RBuilder.renderDetail(detail: ExecutionValue) {
        if (detail is BinaryExecutionValue) {

            val screenshotPngUrl = detail.cache("img") {
                val base64 = IoUtils.base64Encode(detail.value)
                "data:png/png;base64,$base64"
            }

            img {
                attrs {
                    width = "100%"
                    src = screenshotPngUrl
                }
            }
        }
        else if (detail is ScalarExecutionValue) {
            +"${detail.get()}"
        }
    }


    private fun RBuilder.renderAttribute(
            attributeName: AttributeName
    ) {
        props.attributeController.child(this) {
            attrs {
                this.graphStructure = props.graphStructure
                this.objectLocation = props.objectLocation
                this.attributeName = attributeName
            }
        }
    }
}