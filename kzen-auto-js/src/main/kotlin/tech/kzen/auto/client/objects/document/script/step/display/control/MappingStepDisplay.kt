package tech.kzen.auto.client.objects.document.script.step.display.control

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
import tech.kzen.auto.client.wrap.material.SubdirectoryArrowLeftIcon
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
import tech.kzen.lib.common.reflect.Reflect
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface MappingStepDisplayProps: StepDisplayProps {
    var attributeController: AttributeController.Wrapper
    var scriptCommander: ScriptCommander
    var stepControllerHandle: StepController.Handle
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class MappingStepDisplay(
        props: MappingStepDisplayProps
):
        RPureComponent<MappingStepDisplayProps, State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val itemsAttributeName = AttributeName("items")

        private val stepWidth = StepController.width.minus(2.em)
        private val overlapTop = 4.px
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
            objectLocation: ObjectLocation,
            private val attributeController: AttributeController.Wrapper,
            private val scriptCommander: ScriptCommander,
            private val stepControllerHandle: StepController.Handle
    ) :
            StepDisplayWrapper(objectLocation)
    {
        override fun ChildrenBuilder.child(block: StepDisplayProps.() -> Unit) {
            MappingStepDisplay::class.react {
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

                borderCollapse = BorderCollapse.collapse
            }

            tbody {
                tr {
                    td {
                        css {
                            padding = Padding(0.px, 0.px, 0.px, 0.px)
                        }

                        onMouseOver = {
                            this@MappingStepDisplay.onMouseOver(it)
                        }
                        onMouseOut = {
                            this@MappingStepDisplay.onMouseOut(it)
                        }

                        renderHeader(isNextToRun, imperativeState/*, isRunning*/)
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

                        renderBody(isNextToRun, imperativeState, isRunning)
                    }
                    td {
                        renderBranch()
                    }
                }
            }
        }
    }


    private fun ChildrenBuilder.renderHeader(
            isNextToRun: Boolean,
            imperativeState: ImperativeState?,
//            isRunning: Boolean
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

            +"[Header]"
//            StepHeader::class.react {
//                hoverSignal = this@MappingStepDisplay.hoverSignal
//
//                attributeNesting = props.common.attributeNesting
//                objectLocation = props.common.objectLocation
//                graphStructure = props.common.clientState.graphStructure()
//
//                this.imperativeState = imperativeState
////                this.isRunning = isRunning
//            }
        }
    }


    private fun ChildrenBuilder.renderBody(
            isNextToRun: Boolean,
            imperativeState: ImperativeState?,
            isRunning: Boolean
    ) {
        val internalControlState = imperativeState
                ?.controlState as? InternalControlState

        val inBranch = ! isNextToRun &&
                ! isRunning &&
                internalControlState?.branchIndex == 0

        div {
            css {
                width = stepWidth
                padding = Padding(0.px, 1.em, 0.px, 1.em)
                filter = dropShadow(0.px, 1.px, 1.px, NamedColor.gray)

                height = 100.pct

                backgroundColor = when {
                    imperativeState?.previous is ExecutionSuccess ->
                        Color("#00b467")

                    inBranch ->
                        EdgeController.goldLight75

                    else ->
                        NamedColor.white
                }
            }

            props.attributeController.child(this) {
                this.clientState = props.common.clientState
                this.objectLocation = props.common.objectLocation
                this.attributeName = itemsAttributeName
            }

            val controlValue = internalControlState?.value
            if (controlValue != null) {
                val values = (controlValue as MapExecutionValue).values
                val index = (values[ListMapping.indexKey] as NumberExecutionValue).value.toInt()
                val buffer = (values[ListMapping.bufferKey] as ListExecutionValue).values

                div {
                    css {
                        marginTop = 0.5.em
                        backgroundColor = Color("rgba(0, 0, 0, 0.04)")
                        padding = Padding(0.5.em, 0.5.em, 0.5.em, 0.5.em)
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
                div {
                    css {
                        marginTop = 0.5.em
                        backgroundColor = Color("rgba(0, 0, 0, 0.04)")
                        padding = Padding(0.px, 0.5.em, 0.px, 0.5.em)
                    }

                    val textValues = (previousValue.value as ListExecutionValue)
                            .values.map { it.get().toString() }

                    +"$textValues"
                }
            }
        }
    }


    private fun ChildrenBuilder.renderBranch(
//            imperativeState: ImperativeState
    ) {
        div {
            css {
                marginBottom = overlapTop
            }

            div {
                css {
                    width = StepController.width.div(2)
                    display = Display.inlineBlock
                    marginLeft = 3.px
                }

                +"Each"
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
                }

                MappingBranchDisplay::class.react {
                    branchAttributePath = ScriptDocument.stepsAttributePath

                    this.stepController = props.stepControllerHandle.wrapper!!
                    this.scriptCommander = props.scriptCommander

                    this.clientState = props.common.clientState
                    this.objectLocation = props.common.objectLocation
                    this.imperativeModel = props.common.imperativeModel
                }

                SubdirectoryArrowLeftIcon::class.react {
                    style = jso {
                        fontSize = 3.em
                        marginTop = (-3.25).em
                        marginBottom = 0.25.em
                    }
                }
            }
        }
    }
}