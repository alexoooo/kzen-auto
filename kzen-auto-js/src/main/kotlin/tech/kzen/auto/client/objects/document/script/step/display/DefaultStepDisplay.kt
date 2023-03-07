package tech.kzen.auto.client.objects.document.script.step.display
//
//import kotlinx.css.*
//import kotlinx.html.js.onMouseOutFunction
//import kotlinx.html.js.onMouseOverFunction
//import kotlinx.html.title
//import react.RBuilder
//import react.RHandler
//import react.RPureComponent
//import react.State
//import react.dom.attrs
//import react.dom.img
//import styled.css
//import styled.styledDiv
//import styled.styledSpan
//import tech.kzen.auto.client.objects.document.common.AttributeController
//import tech.kzen.auto.client.objects.document.script.step.StepController
//import tech.kzen.auto.client.objects.document.script.step.header.StepHeader
//import tech.kzen.auto.client.wrap.material.MaterialCardContent
//import tech.kzen.auto.client.wrap.material.MaterialPaper
//import tech.kzen.auto.client.wrap.reactStyle
//import tech.kzen.auto.common.paradigm.common.model.*
//import tech.kzen.auto.common.paradigm.imperative.model.ImperativePhase
//import tech.kzen.auto.common.paradigm.imperative.model.ImperativeState
//import tech.kzen.auto.common.paradigm.imperative.util.ImperativeUtils
//import tech.kzen.auto.common.util.AutoConventions
//import tech.kzen.lib.common.model.attribute.AttributeName
//import tech.kzen.lib.common.model.locate.ObjectLocation
//import tech.kzen.lib.common.model.structure.metadata.ObjectMetadata
//import tech.kzen.lib.common.reflect.Reflect
//import tech.kzen.lib.platform.IoUtils
//
//
////---------------------------------------------------------------------------------------------------------------------
//external interface DefaultStepDisplayProps: StepDisplayProps {
//    var attributeController: AttributeController.Wrapper
//}
//
//
//external interface DefaultStepDisplayState: State {
//
//}
//
//
////---------------------------------------------------------------------------------------------------------------------
//@Suppress("unused")
//class DefaultStepDisplay(
//        props: DefaultStepDisplayProps
//):
//        RPureComponent<DefaultStepDisplayProps, DefaultStepDisplayState>(props)
//{
//    //-----------------------------------------------------------------------------------------------------------------
////    companion object {
////        val wrapperName = ObjectName("DefaultStepDisplay")
////    }
//
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    @Reflect
//    class Wrapper(
//            objectLocation: ObjectLocation,
//            private val attributeController: AttributeController.Wrapper
//    ):
//            StepDisplayWrapper(objectLocation)
//    {
//        override fun child(input: RBuilder, handler: RHandler<StepDisplayProps>) {
//            input.child(DefaultStepDisplay::class) {
//                attrs {
//                    this.attributeController = this@Wrapper.attributeController
//                }
//
//                handler()
//            }
//        }
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    private var hoverSignal = StepHeader.HoverSignal()
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override fun DefaultStepDisplayState.init(props: DefaultStepDisplayProps) {
////        hoverCard = false
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    private fun onMouseOver() {
//        hoverSignal.triggerMouseOver()
//    }
//
//
//    private fun onMouseOut() {
//        hoverSignal.triggerMouseOut()
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override fun RBuilder.render() {
//        styledSpan {
//            css {
//                width = StepController.width
//            }
//
//            attrs {
//                onMouseOverFunction = {
//                    onMouseOver()
//                }
//
//                onMouseOutFunction = {
//                    onMouseOut()
//                }
//            }
//
//            renderCard()
//        }
//    }
//
//
//    private fun RBuilder.renderCard() {
//        val imperativeState =
//                if (! props.common.isActive()) {
//                    null
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
////        +"imperativeModel: $imperativeState | ${props.common.objectLocation.objectPath} | ${props.common.imperativeModel}"
//
//        val isRunning = props.common.isRunning()
//        val nextToRun = ImperativeUtils.next(
//                props.common.clientState.graphStructure(), props.common.imperativeModel)
//        val isNextToRun = props.common.objectLocation == nextToRun
//
//        val objectMetadata = props
//            .common
//            .clientState
//            .graphStructure()
//            .graphMetadata
//            .objectMetadata[props.common.objectLocation]!!
//
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
//
//        child(MaterialPaper::class) {
//            attrs {
//                style = reactStyles
//            }
//
//            child(MaterialCardContent::class) {
//                child(StepHeader::class) {
//                    attrs {
//                        hoverSignal = this@DefaultStepDisplay.hoverSignal
//
//                        attributeNesting = props.common.attributeNesting
//                        objectLocation = props.common.objectLocation
//                        graphStructure = props.common.clientState.graphStructure()
//
//                        this.imperativeState = imperativeState
//                        this.isRunning = isRunning
//
//                        managed = props.common.managed
//                        first = props.common.first
//                        last = props.common.last
//                    }
//                }
//
//                styledDiv {
//                    css {
//                        marginBottom = (-1.5).em
//                    }
//
//                    renderBody(objectMetadata, imperativeState)
//                }
//            }
//        }
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    private fun RBuilder.renderBody(
//            objectMetadata: ObjectMetadata,
//            imperativeState: ImperativeState?
//    ) {
//        for (e in objectMetadata.attributes.values) {
//            if (AutoConventions.isManaged(e.key) || props.common.managed) {
//                continue
//            }
//
//            styledDiv {
//                css {
//                    marginBottom = 0.5.em
//                }
//
//                renderAttribute(e.key)
//            }
//        }
//
//        (imperativeState?.previous as? ExecutionSuccess)?.value?.let {
//            renderValue(it)
//        }
//
//        (imperativeState?.previous as? ExecutionSuccess)?.detail?.let {
//            renderDetail(it)
//        }
//    }
//
//
//    private fun RBuilder.renderValue(value: ExecutionValue) {
//        if (value is NullExecutionValue) {
//            return
//        }
//
//        styledDiv {
//            attrs {
//                title = "Result"
//            }
//
//            css {
//                padding(0.em, 0.5.em, 0.5.em, 0.5.em)
//            }
//
//            styledDiv {
//                css {
//                    backgroundColor = Color("rgba(0, 0, 0, 0.04)")
//                    padding(0.5.em)
//                }
//
//                when (value) {
//                    is ScalarExecutionValue -> {
//                        +"${value.get()}"
//                    }
//
//                    is ListExecutionValue -> {
//                        val textValues = value.values.map { it.get().toString() }
//                        +"$textValues"
//                    }
//
//                    else -> {
//                        +"$value"
//                    }
//                }
//            }
//        }
//    }
//
//
//    private fun RBuilder.renderDetail(detail: ExecutionValue) {
//        when (detail) {
//            is BinaryExecutionValue -> {
//                val screenshotPngUrl = detail.cache("img") {
//                    val base64 = IoUtils.base64Encode(detail.value)
//                    "data:png/png;base64,$base64"
//                }
//
//                img {
//                    attrs {
//                        width = "100%"
//                        src = screenshotPngUrl
//                    }
//                }
//            }
//
//            is ScalarExecutionValue -> {
//                +"${detail.get()}"
//            }
//
//            is ListExecutionValue -> {
//                val valueStrings = detail.values.map { it.get().toString() }
//                +"$valueStrings"
//            }
//
//            is NullExecutionValue -> {}
//
//            else -> {
//                +"Detail: $detail"
//            }
//        }
//    }
//
//
//    private fun RBuilder.renderAttribute(
//            attributeName: AttributeName
//    ) {
//        props.attributeController.child(this) {
//            attrs {
//                this.clientState = props.common.clientState
//                this.objectLocation = props.common.objectLocation
//                this.attributeName = attributeName
//            }
//        }
//    }
//}