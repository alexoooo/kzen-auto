package tech.kzen.auto.client.objects.document.script.step.display.control

import kotlinx.css.*
import kotlinx.html.js.onMouseOutFunction
import kotlinx.html.js.onMouseOverFunction
import org.w3c.dom.events.Event
import react.*
import react.dom.attrs
import react.dom.br
import react.dom.td
import styled.*
import tech.kzen.auto.client.objects.document.common.AttributeController
import tech.kzen.auto.client.objects.document.script.command.ScriptCommander
import tech.kzen.auto.client.objects.document.script.step.StepController
import tech.kzen.auto.client.objects.document.script.step.display.StepDisplayProps
import tech.kzen.auto.client.objects.document.script.step.display.StepDisplayWrapper
import tech.kzen.auto.client.objects.document.script.step.header.StepHeader
import tech.kzen.auto.client.wrap.material.ArrowForwardIcon
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeState
import tech.kzen.auto.common.paradigm.imperative.model.control.InternalControlState
import tech.kzen.auto.common.paradigm.imperative.util.ImperativeUtils
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


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
        private val stepWidth = StepController.width.minus(2.em)
        private val overlapTop = 4.px

//        private const val tableBorders = true
        private const val tableBorders = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var attributeController: AttributeController.Wrapper,
            var scriptCommander: ScriptCommander,

            var stepControllerHandle: StepController.Handle,

            common: Common
    ): StepDisplayProps(common)


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
            objectLocation: ObjectLocation,
            private val attributeController: AttributeController.Wrapper,
            private val scriptCommander: ScriptCommander,
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
                    attributeController = this@Wrapper.attributeController
                    scriptCommander = this@Wrapper.scriptCommander
                    stepControllerHandle = this@Wrapper.stepControllerHandle
                }

                handler()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var hoverSignal = StepHeader.HoverSignal()


    //-----------------------------------------------------------------------------------------------------------------
    @Suppress("UNUSED_PARAMETER")
    private fun onMouseOver(event: Event) {
        hoverSignal.triggerMouseOver()
    }


    @Suppress("UNUSED_PARAMETER")
    private fun onMouseOut(event: Event) {
        hoverSignal.triggerMouseOut()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val imperativeState = props
                .common
                .imperativeModel
                .frames
                .lastOrNull()
                ?.states
                ?.get(props.common.objectLocation.objectPath)
        val isRunning = props.common.isRunning()

        val nextToRun = ImperativeUtils.next(
                props.common.clientState.graphStructure(), props.common.imperativeModel)

        val isNextToRun = nextToRun == props.common.objectLocation

        styledTable {
            css {
                // https://stackoverflow.com/a/24594811/1941359
                height = 100.pct

                if (tableBorders) {
                    borderWidth = 1.px
                    borderStyle = BorderStyle.solid
                }

                borderCollapse = BorderCollapse.collapse
            }

            styledTbody {
                css {
                    if (tableBorders) {
                        borderWidth = 1.px
                        borderStyle = BorderStyle.solid
                    }
                }

                styledTr {
                    styledTd {
                        css {
                            padding(0.px)
                        }

                        attrs {
                            onMouseOverFunction = ::onMouseOver
                            onMouseOutFunction = ::onMouseOut
                        }

                        renderHeader(isNextToRun, imperativeState, isRunning)
                    }

                    td {}
                }

                styledTr {
                    styledTd {
                        css {
                            verticalAlign = VerticalAlign.top
                            height = 100.pct
                            padding(0.px)
                        }

                        renderCondition(isNextToRun, imperativeState, isRunning)
                    }
                    styledTd {
                        css {
                            if (tableBorders) {
                                borderWidth = 1.px
                                borderStyle = BorderStyle.solid
                            }
                        }
                        renderThenBranch()
                    }
                }

                styledTr {
                    styledTd {
                        css {
                            verticalAlign = VerticalAlign.top
                            height = 100.pct
                            padding(0.px)

                            if (tableBorders) {
                                borderWidth = 1.px
                                borderStyle = BorderStyle.solid
                            }
                        }

                        renderElseSegment(isNextToRun, imperativeState, isRunning)
                    }

                    td {
                        renderElseBranch()
                    }
                }
            }
        }
    }


    private fun RBuilder.renderHeader(
            isNextToRun: Boolean,
            imperativeState: ImperativeState?,
            isRunning: Boolean
    ) {
        styledDiv {
            css {
                width = stepWidth
                padding(16.px, 16.px, 0.px, 16.px)
                borderTopLeftRadius = 3.px
                borderTopRightRadius = 3.px
                filter = "drop-shadow(0 1px 1px gray)"

                backgroundColor = when {
                    imperativeState?.previous is ExecutionSuccess ->
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

                    attributeNesting = props.common.attributeNesting
                    objectLocation = props.common.objectLocation
                    graphStructure = props.common.clientState.graphStructure()

                    this.imperativeState = imperativeState
                    this.isRunning = isRunning
                }
            }
        }
    }


    private fun RBuilder.renderCondition(
            isNextToRun: Boolean,
            imperativeState: ImperativeState?,
            isRunning: Boolean
    ) {
        val inThenBranch = ! isNextToRun &&
                ! isRunning &&
                imperativeState?.controlState is InternalControlState &&
                (imperativeState.controlState as InternalControlState).branchIndex == 0

        styledDiv {
            css {
                width = stepWidth
                padding(left = 1.em, right = 1.em)
                filter = "drop-shadow(0 1px 1px gray)"

                height = 100.pct

                backgroundColor = when {
                    imperativeState?.previous is ExecutionSuccess ->
                        Color("#00b467")

                    inThenBranch ->
                        Color.gold.lighten(75)

                    else ->
                        Color.white
                }
            }

            props.attributeController.child(this) {
                attrs {
                    this.clientState = props.common.clientState
                    this.objectLocation = props.common.objectLocation
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
                width = 100.pct
                marginBottom = overlapTop
            }

            styledDiv {
                css {
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
                    width = 100.pct.minus(3.em)
                    display = Display.inlineBlock
                    marginTop = (-4.5).em
                }

                child(ConditionalBranchDisplay::class) {
                    attrs {
                        branchAttributePath = AttributePath.ofName(thenAttributeName)

                        this.stepController = props.stepControllerHandle.wrapper!!
                        this.scriptCommander = props.scriptCommander

                        this.clientState = props.common.clientState
                        this.objectLocation = props.common.objectLocation
                        this.imperativeModel = props.common.imperativeModel
                    }
                }
            }
        }
    }


    private fun RBuilder.renderElseSegment(
            isNextToRun: Boolean,
            imperativeState: ImperativeState?,
            isRunning: Boolean
    ) {
        val inElseBranch = ! isNextToRun &&
                ! isRunning &&
                imperativeState?.controlState is InternalControlState &&
                (imperativeState.controlState as InternalControlState).branchIndex == 1

        styledDiv {
            css {
                padding(left = 1.em, right = 1.em)
                borderBottomLeftRadius = 3.px
                borderBottomRightRadius = 3.px
                filter = "drop-shadow(0 1px 1px gray)"

                backgroundColor = when {
                    imperativeState?.previous is ExecutionSuccess ->
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
                width = 100.pct
            }

            styledDiv {
                css {
                    width = 100.pct
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
                    width = 100.pct.minus(3.em)
                }

                child(ConditionalBranchDisplay::class) {
                    attrs {
                        branchAttributePath = AttributePath.ofName(elseAttributeName)

                        this.stepController = props.stepControllerHandle.wrapper!!
                        this.scriptCommander = props.scriptCommander

//                        this.graphStructure = props.common.graphStructure
                        this.clientState = props.common.clientState
                        this.objectLocation = props.common.objectLocation
                        this.imperativeModel = props.common.imperativeModel
                    }
                }
            }
        }
    }
}