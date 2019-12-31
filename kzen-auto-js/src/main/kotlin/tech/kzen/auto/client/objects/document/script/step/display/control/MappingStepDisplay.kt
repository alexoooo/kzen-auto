package tech.kzen.auto.client.objects.document.script.step.display.control

import kotlinx.css.*
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
import tech.kzen.auto.client.objects.document.script.command.ScriptCommander
import tech.kzen.auto.client.objects.document.script.step.StepController
import tech.kzen.auto.client.objects.document.script.step.display.StepDisplayProps
import tech.kzen.auto.client.objects.document.script.step.display.StepDisplayWrapper
import tech.kzen.auto.client.objects.document.script.step.header.StepHeader
import tech.kzen.auto.client.wrap.ArrowForwardIcon
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.script.ScriptDocument
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeState
import tech.kzen.auto.common.paradigm.imperative.model.control.InternalControlState
import tech.kzen.auto.common.paradigm.imperative.util.ImperativeUtils
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure


@Suppress("unused")
class MappingStepDisplay(
        props: Props
):
        RPureComponent<MappingStepDisplay.Props, RState>(props)
{

    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val itemsAttributeName = AttributeName("items")
//        private val thenAttributeName = AttributeName("then")
//        private val elseAttributeName = AttributeName("else")
        private val stepWidth = 18.em
        private val overlapTop = 4.px
    }



    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var attributeController: AttributeController.Wrapper,
            var scriptCommander: ScriptCommander,
            var stepControllerHandle: StepController.Handle,

            graphStructure: GraphStructure,
            objectLocation: ObjectLocation,
            attributeNesting: AttributeNesting,
            imperativeModel: ImperativeModel
    ) : StepDisplayProps(
            graphStructure, objectLocation, attributeNesting, imperativeModel
    )


    @Suppress("unused")
    class Wrapper(
            objectLocation: ObjectLocation,
            private val attributeController: AttributeController.Wrapper,
            private val scriptCommander: ScriptCommander,
            private val stepControllerHandle: StepController.Handle
    ) :
            StepDisplayWrapper(objectLocation) {
        override fun child(
                input: RBuilder,
                handler: RHandler<StepDisplayProps>
        ): ReactElement {
            return input.child(MappingStepDisplay::class) {
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
//                    td {}
                }


                styledTr {
                    styledTd {
                        css {
                            verticalAlign = VerticalAlign.top
                            height = 100.pct
                            padding(0.px)
                        }

                        renderReference(isNextToRun, imperativeState)
                    }
                    styledTd {
                        css {
//                            borderWidth = 1.px
//                            borderStyle = BorderStyle.solid

//                            padding(0.px)
                        }

                        renderBranch()
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
                    hoverSignal = this@MappingStepDisplay.hoverSignal

                    attributeNesting = props.attributeNesting
                    objectLocation = props.objectLocation
                    graphStructure = props.graphStructure

                    this.imperativeState = imperativeState
                }
            }
        }
    }


    private fun RBuilder.renderReference(
            isNextToRun: Boolean,
            imperativeState: ImperativeState?
    ) {
        val inBranch = !isNextToRun &&
                !(imperativeState?.running ?: false) &&
                imperativeState?.controlState is InternalControlState &&
                imperativeState.controlState.branchIndex == 0

        styledDiv {
            css {
                width = stepWidth
                padding(left = 1.em, right = 1.em)
                filter = "drop-shadow(0 1px 1px gray)"

                height = 100.pct

                backgroundColor = when {
                    imperativeState?.previous is ExecutionSuccess ->
                        Color("#00b467")

                    inBranch ->
                        Color.gold.lighten(75)

                    else ->
                        Color.white
                }
            }

            props.attributeController.child(this) {
                attrs {
                    this.graphStructure = props.graphStructure
                    this.objectLocation = props.objectLocation
                    this.attributeName = itemsAttributeName
                }
            }
        }
    }


    private fun RBuilder.renderBranch(
//            imperativeState: ImperativeState
    ) {
        styledDiv {
            css {
                marginBottom = overlapTop
            }

            styledDiv {
                css {
                    width = 10.em
                    display = Display.inlineBlock
                    marginLeft = 3.px
                }

                +"Each"
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

                child(MappingBranchDisplay::class) {
                    attrs {
                        branchAttributePath = ScriptDocument.stepsAttributePath

                        this.stepController = props.stepControllerHandle.wrapper!!
                        this.scriptCommander = props.scriptCommander

                        this.graphStructure = props.graphStructure
                        this.objectLocation = props.objectLocation
                        this.imperativeModel = props.imperativeModel
                    }
                }
            }
        }
    }
}