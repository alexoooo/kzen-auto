package tech.kzen.auto.client.objects.document.script.step.display.control

import web.cssom.*
import emotion.react.css
import js.core.jso
import react.ChildrenBuilder
import react.State
import react.dom.events.MouseEvent
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.tr
import react.react
import tech.kzen.auto.client.objects.document.common.AttributeController
import tech.kzen.auto.client.objects.document.graph.EdgeController
import tech.kzen.auto.client.objects.document.script.command.ScriptCommander
import tech.kzen.auto.client.objects.document.script.step.StepController
import tech.kzen.auto.client.objects.document.script.step.display.StepDisplayProps
import tech.kzen.auto.client.objects.document.script.step.display.StepDisplayWrapper
import tech.kzen.auto.client.objects.document.script.step.header.StepHeader
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.ArrowForwardIcon
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeState
import tech.kzen.auto.common.paradigm.imperative.model.control.InternalControlState
import tech.kzen.auto.common.paradigm.imperative.util.ImperativeUtils
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


//---------------------------------------------------------------------------------------------------------------------
external interface ConditionalStepDisplayProps: StepDisplayProps {
    var attributeController: AttributeController.Wrapper
    var scriptCommander: ScriptCommander

    var stepControllerHandle: StepController.Handle
}
//            common: StepDisplayPropsCommon
//    ): StepDisplayProps(common)


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class ConditionalStepDisplay(
        props: ConditionalStepDisplayProps
):
        RPureComponent<ConditionalStepDisplayProps, State>(props)
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
    @Reflect
    class Wrapper(
            objectLocation: ObjectLocation,
            private val attributeController: AttributeController.Wrapper,
            private val scriptCommander: ScriptCommander,
            private val stepControllerHandle: StepController.Handle
    ):
            StepDisplayWrapper(objectLocation)
    {
        override fun ChildrenBuilder.child(block: StepDisplayProps.() -> Unit) {
            ConditionalStepDisplay::class.react {
                attributeController = this@Wrapper.attributeController
                scriptCommander = this@Wrapper.scriptCommander
                stepControllerHandle = this@Wrapper.stepControllerHandle

                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var hoverSignal = StepHeader.HoverSignal()


    //-----------------------------------------------------------------------------------------------------------------
    @Suppress("UNUSED_PARAMETER")
    private fun onMouseOver(event: MouseEvent<*, *>) {
        hoverSignal.triggerMouseOver()
    }


    @Suppress("UNUSED_PARAMETER")
    private fun onMouseOut(event: MouseEvent<*, *>) {
        hoverSignal.triggerMouseOut()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
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

        table {
            css {
                // https://stackoverflow.com/a/24594811/1941359
                height = 100.pct

                if (tableBorders) {
                    borderWidth = 1.px
                    borderStyle = LineStyle.solid
                }

                borderCollapse = BorderCollapse.collapse
            }

            tbody {
                css {
                    if (tableBorders) {
                        borderWidth = 1.px
                        borderStyle = LineStyle.solid
                    }
                }

                tr {
                    td {
                        css {
                            padding = Padding(0.px, 0.px, 0.px, 0.px)
                        }

                        onMouseOver = {
                            this@ConditionalStepDisplay.onMouseOver(it)
                        }

                        onMouseOut = {
                            this@ConditionalStepDisplay.onMouseOut(it)
                        }

                        renderHeader(isNextToRun, imperativeState, isRunning)
                    }

                    td {}
                }

                tr {
                    td {
                        css {
                            verticalAlign = VerticalAlign.top
                            height = 100.pct
                            padding = Padding(0.px, 0.px, 0.px, 0.px)
                        }

                        renderCondition(isNextToRun, imperativeState, isRunning)
                    }
                    td {
                        css {
                            if (tableBorders) {
                                borderWidth = 1.px
                                borderStyle = LineStyle.solid
                            }
                        }
                        renderThenBranch()
                    }
                }

                tr {
                    td {
                        css {
                            verticalAlign = VerticalAlign.top
                            height = 100.pct
                            padding = Padding(0.px, 0.px, 0.px, 0.px)

                            if (tableBorders) {
                                borderWidth = 1.px
                                borderStyle = LineStyle.solid
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


    private fun ChildrenBuilder.renderHeader(
            isNextToRun: Boolean,
            imperativeState: ImperativeState?,
            isRunning: Boolean
    ) {
        div {
            css {
                width = stepWidth
                padding = Padding(16.px, 16.px, 0.px, 16.px)
                borderTopLeftRadius = 3.px
                borderTopRightRadius = 3.px
                filter = dropShadow(0.px, 1.px, 1.px, NamedColor.gray)

                backgroundColor = when {
                    imperativeState?.previous is ExecutionSuccess ->
                        Color("#00b467")

                    isNextToRun ->
                        EdgeController.goldLight50

                    else ->
                        NamedColor.white
                }
            }

            StepHeader::class.react {
                hoverSignal = this@ConditionalStepDisplay.hoverSignal

                attributeNesting = props.common.attributeNesting
                objectLocation = props.common.objectLocation
                graphStructure = props.common.clientState.graphStructure()

                this.imperativeState = imperativeState
                this.isRunning = isRunning
            }
        }
    }


    private fun ChildrenBuilder.renderCondition(
            isNextToRun: Boolean,
            imperativeState: ImperativeState?,
            isRunning: Boolean
    ) {
        val inThenBranch = ! isNextToRun &&
                ! isRunning &&
                imperativeState?.controlState is InternalControlState &&
                (imperativeState.controlState as InternalControlState).branchIndex == 0

        div {
            css {
                width = stepWidth
                padding = Padding(0.em, 1.em, 0.em, 1.em)
                filter = dropShadow(0.px, 1.px, 1.px, NamedColor.gray)

                height = 100.pct

                backgroundColor = when {
                    imperativeState?.previous is ExecutionSuccess ->
                        Color("#00b467")

                    inThenBranch ->
                        EdgeController.goldLight75

                    else ->
                        NamedColor.white
                }
            }

            props.attributeController.child(this) {
                this.clientState = props.common.clientState
                this.objectLocation = props.common.objectLocation
                this.attributeName = conditionAttributeName
            }
        }
    }


    private fun ChildrenBuilder.renderThenBranch(
//            imperativeState: ImperativeState
    ) {
        div {
            css {
                width = 100.pct
                marginBottom = overlapTop
            }

            div {
                css {
                    display = Display.inlineBlock
                    marginLeft = 3.px
                }

                +"Then"
                br {}
                ArrowForwardIcon::class.react {
                    style = jso {
                        fontSize = 3.em
                    }
                }
            }

            div {
                css {
                    width = 100.pct.minus(3.em)
                    display = Display.inlineBlock
                    marginTop = (-4.5).em
                }

                ConditionalBranchDisplay::class.react {
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


    private fun ChildrenBuilder.renderElseSegment(
            isNextToRun: Boolean,
            imperativeState: ImperativeState?,
            isRunning: Boolean
    ) {
        val inElseBranch = ! isNextToRun &&
                ! isRunning &&
                imperativeState?.controlState is InternalControlState &&
                (imperativeState.controlState as InternalControlState).branchIndex == 1

        div {
            css {
                padding = Padding(0.px, 1.em, 0.px, 1.em)
                borderBottomLeftRadius = 3.px
                borderBottomRightRadius = 3.px
                filter = dropShadow(0.px, 1.px, 1.px, NamedColor.gray)

                backgroundColor = when {
                    imperativeState?.previous is ExecutionSuccess ->
                        Color("#00b467")

                    inElseBranch ->
                        EdgeController.goldLight75

                    else ->
                        NamedColor.white
                }

                height = 100.pct
            }
            +"Otherwise"
        }
    }


    private fun ChildrenBuilder.renderElseBranch(
//            imperativeState: ImperativeState
    ) {
        div {
            css {
                marginBottom = 2.times(overlapTop)
                width = 100.pct
            }

            div {
                css {
                    width = 100.pct
                    display = Display.inlineBlock
                    marginLeft = 3.px
                }

                +"Else"
                br {}
                ArrowForwardIcon::class.react {
                    style = jso {
                        fontSize = 3.em
                    }
                }
            }

            div {
                css {
                    display = Display.inlineBlock
                    marginTop = (-4.5).em
                    width = 100.pct.minus(3.em)
                }

                ConditionalBranchDisplay::class.react {
                    branchAttributePath = AttributePath.ofName(elseAttributeName)

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