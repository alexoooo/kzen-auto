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
import react.dom.div
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
import tech.kzen.auto.client.wrap.SubdirectoryArrowLeftIcon
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.script.ScriptDocument
import tech.kzen.auto.common.objects.document.script.control.ListMapping
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.common.model.ListExecutionValue
import tech.kzen.auto.common.paradigm.common.model.MapExecutionValue
import tech.kzen.auto.common.paradigm.common.model.NumberExecutionValue
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeState
import tech.kzen.auto.common.paradigm.imperative.model.control.InternalControlState
import tech.kzen.auto.common.paradigm.imperative.util.ImperativeUtils
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.locate.ObjectLocation


@Suppress("unused")
class MappingStepDisplay(
        props: Props
):
        RPureComponent<MappingStepDisplay.Props, RState>(props)
{

    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val itemsAttributeName = AttributeName("items")

        private val stepWidth = 18.em
        private val overlapTop = 4.px
    }


    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var attributeController: AttributeController.Wrapper,
            var scriptCommander: ScriptCommander,
            var stepControllerHandle: StepController.Handle,

            common: Common
    ): StepDisplayProps(common)


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
                .common
                .imperativeModel
                .frames
                .lastOrNull()
                ?.states
                ?.get(props.common.objectLocation.objectPath)
        val isRunning = props.common.isRunning()

        val nextToRun = ImperativeUtils.next(
                props.common.graphStructure, props.common.imperativeModel)

        val isNextToRun = nextToRun == props.common.objectLocation

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

                        renderBody(isNextToRun, imperativeState, isRunning)
                    }
                    styledTd {
//                        css {
//                            borderWidth = 1.px
//                            borderStyle = BorderStyle.solid
//                            padding(0.px)
//                        }

                        renderBranch()
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

                    attributeNesting = props.common.attributeNesting
                    objectLocation = props.common.objectLocation
                    graphStructure = props.common.graphStructure

                    this.imperativeState = imperativeState
                    this.isRunning = isRunning
                }
            }
        }
    }


    private fun RBuilder.renderBody(
            isNextToRun: Boolean,
            imperativeState: ImperativeState?,
            isRunning: Boolean
    ) {
        val internalControlState = imperativeState
                ?.controlState as? InternalControlState

        val inBranch = ! isNextToRun &&
                ! isRunning &&
                internalControlState?.branchIndex == 0

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
                    this.graphStructure = props.common.graphStructure
                    this.objectLocation = props.common.objectLocation
                    this.attributeName = itemsAttributeName
                }
            }

            val controlValue = internalControlState?.value
            if (controlValue != null) {
                val values = (controlValue as MapExecutionValue).values
                val index = (values[ListMapping.indexKey] as NumberExecutionValue).value.toInt()
                val buffer = (values[ListMapping.bufferKey] as ListExecutionValue).values

                styledDiv {
                    css {
                        marginTop = 0.5.em
                        backgroundColor = Color("rgba(0, 0, 0, 0.04)")
                        padding(0.5.em)
                    }

                    div {
                        +"Item number: ${index + 1}"
                    }

                    div {
                        +"${buffer.map { it.get() }}"
                    }
                }
            }

            val previousValue = imperativeState?.previous as? ExecutionSuccess
            if (previousValue != null) {
                styledDiv {
                    css {
                        marginTop = 0.5.em
                        backgroundColor = Color("rgba(0, 0, 0, 0.04)")
                        padding(0.5.em)
                    }

                    val textValues = (previousValue.value as ListExecutionValue)
                            .values.map { it.get().toString() }

                    +"$textValues"
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

                        this.graphStructure = props.common.graphStructure
                        this.objectLocation = props.common.objectLocation
                        this.imperativeModel = props.common.imperativeModel
                    }
                }

                child(SubdirectoryArrowLeftIcon::class) {
                    attrs {
                        style = reactStyle {
                            fontSize = 3.em
                            marginTop = (-3.25).em
                            marginBottom = 0.25.em
                        }
                    }
                }
            }
        }
    }
}