package tech.kzen.auto.client.objects.document.script.step.display

import kotlinx.css.*
import kotlinx.css.properties.deg
import kotlinx.css.properties.rotate
import kotlinx.css.properties.transform
import kotlinx.html.js.onMouseOutFunction
import kotlinx.html.js.onMouseOverFunction
import org.w3c.dom.events.Event
import react.RBuilder
import react.RHandler
import react.RState
import react.ReactElement
import react.dom.br
import react.dom.td
import styled.*
import tech.kzen.auto.client.objects.document.common.AttributeController
import tech.kzen.auto.client.objects.document.script.step.StepController
import tech.kzen.auto.client.objects.document.script.step.header.StepHeader
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeState
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeSuccess
import tech.kzen.auto.common.paradigm.imperative.model.control.BranchEvaluationState
import tech.kzen.auto.common.paradigm.imperative.util.ImperativeUtils
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure


@Suppress("unused")
class ConditionalStepDisplay(
        props: Props
):
        RPureComponent<ConditionalStepDisplay.Props, RState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val conditionAttributeName = AttributeName("condition")
        private val thenAttributeName = AttributeName("then")
        private val elseAttributeName = AttributeName("else")
        private val stepWidth = 18.em
        private val overlapTop = 4.px
    }


    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var attributeController: AttributeController.Wrapper,
            var stepControllerHandle: StepController.Handle,

            graphStructure: GraphStructure,
            objectLocation: ObjectLocation,
            attributeNesting: AttributeNesting,
            imperativeModel: ImperativeModel
    ): StepDisplayProps(
            graphStructure, objectLocation, attributeNesting, imperativeModel
    )


    @Suppress("unused")
    class Wrapper(
            objectLocation: ObjectLocation,
            private val attributeController: AttributeController.Wrapper,
            private val stepControllerHandle: StepController.Handle
    ):
            StepDisplayWrapper(objectLocation)
    {
        override fun child(
                input: RBuilder,
                handler: RHandler<StepDisplayProps>
        ): ReactElement {
            return input.child(ConditionalStepDisplay::class) {
                attrs {
                    this.attributeController = this@Wrapper.attributeController
                    this.stepControllerHandle = this@Wrapper.stepControllerHandle
                }

                handler()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var hoverSignal = StepHeader.HoverSignal()


    //-----------------------------------------------------------------------------------------------------------------
    private fun onMouseOver(event: Event) {
        hoverSignal.triggerMouseOver()
    }


    private fun onMouseOut(event: Event) {
        hoverSignal.triggerMouseOut()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val imperativeState = props
                .imperativeModel
                .frames
                .lastOrNull()
                ?.states
                ?.get(props.objectLocation.objectPath)

        val nextToRun = ImperativeUtils.next(
                props.graphStructure, props.imperativeModel)

        val isNextToRun = nextToRun == props.objectLocation

        styledTable {
            css {
                // https://stackoverflow.com/a/24594811/1941359
                height = 100.pct

//                borderWidth = 1.px
//                borderStyle = BorderStyle.solid

                borderCollapse = BorderCollapse.collapse
            }

            styledTbody {
//                css {
//                    borderWidth = 1.px
//                    borderStyle = BorderStyle.solid
//                }

                styledTr {
                    styledTd {
                        css {
                            padding(0.px)
                        }

                        attrs {
                            onMouseOverFunction = ::onMouseOver
                            onMouseOutFunction = ::onMouseOut
                        }

                        renderHeader(isNextToRun, imperativeState)
                    }

                    td {}
                    td {}
                }

                styledTr {
                    styledTd {
                        css {
                            verticalAlign = VerticalAlign.top
                            height = 100.pct
                            padding(0.px)
                        }

                        renderCondition(isNextToRun, imperativeState)
                    }
                    styledTd {
                        css {
//                            borderWidth = 1.px
//                            borderStyle = BorderStyle.solid

//                            padding(0.px)
                        }
                        renderThenBranch()
                    }

                    styledTd {
                        css {
                            verticalAlign = VerticalAlign.bottom
                            padding(0.px)
                        }

                        child(SubdirectoryArrowLeftIcon::class) {
                            attrs {
                                style = reactStyle {
                                    fontSize = 3.em

                                    transform {
                                        rotate((-90).deg)
                                    }
                                }
                            }
                        }
                    }
                }

                styledTr {
                    styledTd {
                        css {
                            verticalAlign = VerticalAlign.top
                            height = 100.pct
                            padding(0.px)
//                            borderWidth = 1.px
//                            borderStyle = BorderStyle.solid
                        }

                        renderElseSegment(isNextToRun, imperativeState)
                    }

                    td {
                        renderElseBranch(/*imperativeState*/)
                    }

                    styledTd {
                        css {
//                            borderWidth = 1.px
//                            borderStyle = BorderStyle.solid

//                            position = Position.relative
//                            height = 100.pct
                            verticalAlign = VerticalAlign.bottom
                        }

//                        child(ArrowDownwardIcon::class) {
//                            attrs {
//                                style = reactStyle {
//                                    fontSize = 3.em
//
////                                    position = Position.absolute
////                                    top = 0.px
//                                }
//                            }
//                        }

                        child(SubdirectoryArrowLeftIcon::class) {
                            attrs {
                                style = reactStyle {
                                    fontSize = 3.em

//                                    position = Position.absolute
//                                    bottom = 0.px

                                    transform {
                                        rotate((-90).deg)
                                    }
                                }
                            }
                        }
                    }
                }

                renderEgressRow()
            }
        }
    }


    private fun RBuilder.renderEgressRow() {
        styledTr {
            styledTd {
                css {
                    textAlign = TextAlign.center
                }

                child(SubdirectoryArrowRightIcon::class) {
                    attrs {
                        style = reactStyle {
                            fontSize = 3.em
                            marginLeft = 6.px

                            transform {
                                rotate(90.deg)
                            }
                        }
                    }
                }
            }

            td {
                child(ArrowBackIcon::class) {
                    attrs {
                        style = reactStyle {
                            fontSize = 3.em
                        }
                    }
                }
            }

            td {
                child(SubdirectoryArrowLeftIcon::class) {
                    attrs {
                        style = reactStyle {
                            fontSize = 3.em
                        }
                    }
                }
            }
        }
    }


    private fun RBuilder.renderHeader(
            isNextToRun: Boolean,
            imperativeState: ImperativeState?
    ) {
        styledDiv {
            css {
                width = 18.em
                padding(16.px, 16.px, 0.px, 16.px)
                borderTopLeftRadius = 3.px
                borderTopRightRadius = 3.px
                filter = "drop-shadow(0 1px 1px gray)"

                backgroundColor = when {
                    imperativeState?.previous is ImperativeSuccess ->
                        Color("#00b467")

                    isNextToRun ->
                        Color.gold.lighten(50)

                    else ->
                        Color.white
                }
            }

            child(StepHeader::class) {
                attrs {
                    hoverSignal = this@ConditionalStepDisplay.hoverSignal

                    attributeNesting = props.attributeNesting
                    objectLocation = props.objectLocation
                    graphStructure = props.graphStructure

                    this.imperativeState = imperativeState
                }
            }
        }
    }


    private fun RBuilder.renderCondition(
            isNextToRun: Boolean,
            imperativeState: ImperativeState?
    ) {
        val inThenBranch = ! isNextToRun &&
                ! (imperativeState?.running ?: false) &&
                imperativeState?.controlState is BranchEvaluationState &&
                imperativeState.controlState.index == 0

        styledDiv {
            css {
                width = stepWidth
                padding(left = 1.em, right = 1.em/*, top = 1.em*/)
//                marginTop = overlapTop.unaryMinus()
                filter = "drop-shadow(0 1px 1px gray)"

                height = 100.pct

                backgroundColor = when {
                    imperativeState?.previous is ImperativeSuccess ->
                        Color("#00b467")

                    inThenBranch ->
                        Color.gold.lighten(75)

                    else ->
                        Color.white
                }
            }

            props.attributeController.child(this) {
                attrs {
                    this.graphStructure = props.graphStructure
                    this.objectLocation = props.objectLocation
                    this.attributeName = conditionAttributeName
                }
            }
        }
    }


    private fun RBuilder.renderThenBranch(
//            imperativeState: ImperativeState
    ) {
        styledDiv {
            css {
//                width = 30.em
                marginBottom = overlapTop
            }

            styledDiv {
                css {
                    width = 10.em
                    display = Display.inlineBlock
                    marginLeft = 3.px
                }

                +"Then"
                br {}
                child(ArrowForwardIcon::class) {
                    attrs {
                        style = reactStyle {
                            fontSize = 3.em
                        }
                    }
                }
            }

            styledDiv {
                css {
                    display = Display.inlineBlock
                    marginTop = (-4.5).em
                }

                child(ConditionalBranchDisplay::class) {
                    attrs {
                        branchAttributePath = AttributePath.ofName(thenAttributeName)

                        this.stepController = props.stepControllerHandle.wrapper!!
                        this.graphStructure = props.graphStructure
                        this.objectLocation = props.objectLocation
                        this.imperativeModel = props.imperativeModel
                    }
                }
            }
        }
    }


    private fun RBuilder.renderElseSegment(
            isNextToRun: Boolean,
            imperativeState: ImperativeState?
    ) {
        val inElseBranch = ! isNextToRun &&
                ! (imperativeState?.running ?: false) &&
                imperativeState?.controlState is BranchEvaluationState &&
                imperativeState.controlState.index == 1

        styledDiv {
            css {
//                marginTop = overlapTop.times(2).unaryMinus()
                width = stepWidth
                padding(left = 1.em, right = 1.em/*, top = 1.em*/)
                borderBottomLeftRadius = 3.px
                borderBottomRightRadius = 3.px
                filter = "drop-shadow(0 1px 1px gray)"

                backgroundColor = when {
                    imperativeState?.previous is ImperativeSuccess ->
                        Color("#00b467")

                    inElseBranch ->
                        Color.gold.lighten(75)

                    else ->
                        Color.white
                }

                height = 100.pct
            }
            +"Otherwise"
        }
    }


    private fun RBuilder.renderElseBranch(
//            imperativeState: ImperativeState
    ) {
        styledDiv {
            css {
                marginBottom = overlapTop.times(2)
            }

            styledDiv {
                css {
                    width = 10.em
                    display = Display.inlineBlock
                    marginLeft = 3.px
                }

                +"Else"
                br {}
                child(ArrowForwardIcon::class) {
                    attrs {
                        style = reactStyle {
                            fontSize = 3.em
                        }
                    }
                }
            }

            styledDiv {
                css {
                    display = Display.inlineBlock
                    marginTop = (-4.5).em
                }

                child(ConditionalBranchDisplay::class) {
                    attrs {
                        branchAttributePath = AttributePath.ofName(elseAttributeName)

                        this.stepController = props.stepControllerHandle.wrapper!!
                        this.graphStructure = props.graphStructure
                        this.objectLocation = props.objectLocation
                        this.imperativeModel = props.imperativeModel
                    }
                }
            }
        }
    }
}