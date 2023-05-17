package tech.kzen.auto.client.objects.document.sequence.step.display

import web.cssom.Color
import web.cssom.Padding
import web.cssom.em
import web.cssom.pct
import emotion.react.css
import mui.material.CardContent
import mui.material.Paper
import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.span
import react.react
import tech.kzen.auto.client.objects.document.common.AttributeController
import tech.kzen.auto.client.objects.document.script.step.StepController
import tech.kzen.auto.client.objects.document.script.step.header.StepHeader
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.common.paradigm.common.model.*
import tech.kzen.auto.common.paradigm.common.v1.trace.model.LogicTracePath
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.ObjectMetadata
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.platform.IoUtils



//---------------------------------------------------------------------------------------------------------------------
external interface SequenceStepDisplayDefaultProps: SequenceStepDisplayProps {
//    var common: SequenceStepDisplayPropsCommon
    var attributeController: AttributeController.Wrapper
}


external interface SequenceStepDisplayDefaultState: State


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class SequenceStepDisplayDefault(
        props: SequenceStepDisplayDefaultProps
):
        RPureComponent<SequenceStepDisplayDefaultProps, SequenceStepDisplayDefaultState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
//    companion object {
//        val wrapperName = ObjectName("DefaultStepDisplay")
//    }


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
            objectLocation: ObjectLocation,
            private val attributeController: AttributeController.Wrapper
    ):
        SequenceStepDisplayWrapper(objectLocation)
    {
        override fun ChildrenBuilder.child(block: SequenceStepDisplayProps.() -> Unit) {
            SequenceStepDisplayDefault::class.react {
                this.attributeController = this@Wrapper.attributeController
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var hoverSignal = StepHeader.HoverSignal()


    //-----------------------------------------------------------------------------------------------------------------
    override fun SequenceStepDisplayDefaultState.init(props: SequenceStepDisplayDefaultProps) {
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
    override fun ChildrenBuilder.render() {
        span {
            css {
                width = StepController.width
            }

            onMouseOver = { onMouseOver() }
            onMouseOut = { onMouseOut() }

            renderCard()
        }
    }


    private fun ChildrenBuilder.renderCard() {
        val imperativeState =
//                if (! props.common.isActive()) {
                    null
//                }
//                else {
//                    props
//                            .common
//                            .imperativeModel
//                            .frames
//                            .lastOrNull()
//                            ?.states
//                            ?.get(props.common.objectLocation.objectPath)
//                }

        val isRunning = props.common.isRunning()
//        val nextToRun = ImperativeUtils.next(
//                props.common.clientState.graphStructure(), props.common.imperativeModel)
//        val isNextToRun = props.common.objectLocation == nextToRun

        val objectMetadata = props
            .common
            .clientState
            .graphStructure()
            .graphMetadata
            .objectMetadata[props.common.objectLocation]!!

//        val reactStyles = reactStyle {
//            val cardColor = when (imperativeState?.phase(isRunning)) {
//                ImperativePhase.Pending ->
//                    if (isNextToRun) {
//                        Color.gold.lighten(50)
//                    }
//                    else {
//                        Color.white
//                    }
//
//                ImperativePhase.Running ->
//                    Color.gold
//
//                ImperativePhase.Success ->
//                    Color("#00b467")
//
//                ImperativePhase.Error ->
//                    Color.red
//
//                null ->
//                    Color.white
////                    Color.gray
//            }
//
//            backgroundColor = cardColor
//
//            width = StepController.width
//        }

        Paper {
            CardContent {
                StepHeader::class.react {
                    hoverSignal = this@SequenceStepDisplayDefault.hoverSignal

                    attributeNesting = props.common.attributeNesting
                    objectLocation = props.common.objectLocation
                    graphStructure = props.common.clientState.graphStructure()

//                    this.imperativeState = imperativeState
                    this.imperativeState = null
                    this.isRunning = isRunning

                    managed = props.common.managed
                    first = props.common.first
                    last = props.common.last
                }

                div {
                    css {
                        marginBottom = (-1.5).em
                    }

                    val progress = props
                        .common
                        .logicTraceSnapshot
                        ?.values
                        ?.get(LogicTracePath.ofObjectLocation(props.common.objectLocation))

                    renderBody(objectMetadata, progress)
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderBody(
            objectMetadata: ObjectMetadata,
//            imperativeState: ImperativeState?
            value: ExecutionValue?
    ) {
        for (e in objectMetadata.attributes.values) {
            if (AutoConventions.isManaged(e.key) || props.common.managed) {
                continue
            }

            div {
                css {
                    marginBottom = 0.5.em
                }

                renderAttribute(e.key)
            }
        }

        value?.let {
            renderValue(it)
        }

//        (imperativeState?.previous as? ExecutionSuccess)?.detail?.let {
//            renderDetail(it)
//        }
    }


    private fun ChildrenBuilder.renderValue(value: ExecutionValue) {
        if (value is NullExecutionValue) {
            return
        }

        div {
            title = "Result"

            css {
                padding = Padding(0.em, 0.5.em, 0.5.em, 0.5.em)
            }

            div {
                css {
                    backgroundColor = Color("rgba(0, 0, 0, 0.04)")
                    padding = Padding(0.5.em, 0.5.em, 0.5.em, 0.5.em)
                }

                when (value) {
                    is ScalarExecutionValue -> {
                        +"${value.get()}"
                    }

                    is ListExecutionValue -> {
                        val textValues = value.values.map { it.get().toString() }
                        +"$textValues"
                    }

                    else -> {
                        +"$value"
                    }
                }
            }
        }
    }


    private fun ChildrenBuilder.renderDetail(detail: ExecutionValue) {
        when (detail) {
            is BinaryExecutionValue -> {
                val screenshotPngUrl = detail.cache("img") {
                    val base64 = IoUtils.base64Encode(detail.value)
                    "data:png/png;base64,$base64"
                }

                img {
                    css {
                        width = 100.pct
                    }
                    src = screenshotPngUrl
                }
            }

            is ScalarExecutionValue -> {
                +"${detail.get()}"
            }

            is ListExecutionValue -> {
                val valueStrings = detail.values.map { it.get().toString() }
                +"$valueStrings"
            }

            is NullExecutionValue -> {}

            else -> {
                +"Detail: $detail"
            }
        }
    }


    private fun ChildrenBuilder.renderAttribute(
            attributeName: AttributeName
    ) {
        props.attributeController.child(this) {
            this.clientState = props.common.clientState
            this.objectLocation = props.common.objectLocation
            this.attributeName = attributeName
        }
    }
}