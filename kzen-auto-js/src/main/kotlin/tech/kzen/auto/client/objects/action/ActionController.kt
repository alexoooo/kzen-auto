package tech.kzen.auto.client.objects.action

import kotlinx.css.*
import kotlinx.html.js.onMouseOutFunction
import kotlinx.html.js.onMouseOverFunction
import kotlinx.html.title
import react.*
import react.dom.div
import react.dom.img
import react.dom.span
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.exec.ExecutionIntent
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.exec.BinaryExecutionValue
import tech.kzen.auto.common.exec.ExecutionPhase
import tech.kzen.auto.common.exec.ExecutionState
import tech.kzen.auto.common.exec.ExecutionSuccess
import tech.kzen.lib.common.api.model.AttributeName
import tech.kzen.lib.common.api.model.AttributePath
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.metadata.model.AttributeMetadata
import tech.kzen.lib.common.structure.notation.edit.RemoveObjectCommand
import tech.kzen.lib.common.structure.notation.edit.ShiftObjectCommand
import tech.kzen.lib.common.structure.notation.model.AttributeNotation
import tech.kzen.lib.common.structure.notation.model.PositionIndex
import tech.kzen.lib.common.structure.notation.model.ScalarAttributeNotation
import tech.kzen.lib.platform.ClassNames
import tech.kzen.lib.platform.IoUtils


class ActionController(
        props: ActionController.Props
):
        RComponent<ActionController.Props, ActionController.State>(props), ExecutionIntent.Observer {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val iconAttribute = AttributePath.ofAttribute(AttributeName("icon"))
        val descriptionAttribute = AttributePath.ofAttribute(AttributeName("description"))

        const val defaultRunIcon = "PlayArrowIcon"
        const val defaultRunDescription = "Run"

        val headerHeight = 2.5.em
    }


    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var objectLocation: ObjectLocation,
            var structure: GraphStructure,

            var state: ExecutionState?
    ): RProps


    class State(
            var hoverCard: Boolean,
            var hoverAction: Boolean,
            var intentToRun: Boolean
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    override fun ActionController.State.init(props: ActionController.Props) {
        hoverCard = false
        hoverAction = false
        intentToRun = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        ClientContext.executionIntent.observe(this)
    }


    override fun componentWillUnmount() {
        ClientContext.executionIntent.unobserve(this)
    }


    override fun componentDidUpdate(
            prevProps: ActionController.Props,
            prevState: ActionController.State,
            snapshot: Any
    ) {
//        console.log("state.hoverCard: ${state.hoverCard} | state.hoverAction: ${state.hoverAction}")
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onExecutionIntent(actionLocation: ObjectLocation?) {
        setState {
            intentToRun = actionLocation == props.objectLocation
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onRun() {
        async {
            ClientContext.executionManager.execute(props.objectLocation)
        }
    }


    private fun onRunEnter() {
        ClientContext.executionIntent.set(props.objectLocation)
    }


    private fun onRunLeave() {
        ClientContext.executionIntent.clearIf(props.objectLocation)
    }


    private fun onRemove() {
        async {
            ClientContext.commandBus.apply(RemoveObjectCommand(
                    props.objectLocation))
        }
    }


    private fun onShiftUp() {
        val packagePath = props.objectLocation.bundlePath
        val packageNotation = props.structure.graphNotation.bundles.values[packagePath]!!
        val index = packageNotation.indexOf(props.objectLocation.objectPath)

        async {
            ClientContext.commandBus.apply(ShiftObjectCommand(
                    props.objectLocation, PositionIndex(index.value - 1)))
        }
    }


    private fun onShiftDown() {
        val packagePath = props.objectLocation.bundlePath
        val packageNotation = props.structure.graphNotation.bundles.values[packagePath]!!
        val index = packageNotation.indexOf(props.objectLocation.objectPath)

        async {
            ClientContext.commandBus.apply(ShiftObjectCommand(
                    props.objectLocation, PositionIndex(index.value + 1)))
        }
    }


    private fun onMouseOver(cardOrActions: Boolean) {
        if (cardOrActions) {
            setState {
                hoverCard = true
            }
        }
        else {
            setState {
                hoverAction = true
            }
        }
    }


    private fun onMouseOut(cardOrActions: Boolean) {
        if (cardOrActions) {
            setState {
                hoverCard = false
            }
        }
        else {
            setState {
                hoverAction = false
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        span {
            styledSpan {
                css {
//                    display = Display.inlineBlock
                    width = 20.em
                }

                attrs {
                    onMouseOverFunction = {
                        onMouseOver(true)
                    }

                    onMouseOutFunction = {
                        onMouseOut(true)
                    }
                }

                renderCard()
            }

            styledSpan {
                css {
//                    display = Display.inlineBlock
                    float = Float.right
//                    backgroundColor = Color.red
//                    marginTop = (-1).px

                    if (! (state.hoverCard || state.hoverAction)) {
                        display = Display.none
                    }
                }

                attrs {
                    onMouseOverFunction = {
//                        console.log("#!@#!@#!@#!@ onMouseOver - ACTIONS!!")
                        onMouseOver(false)
                    }

                    onMouseOutFunction = {
                        onMouseOut(false)
                    }
                }

//                +" x"

//                if (state.hoverCard || state.hoverAction) {
                    renderActions()
//                }
            }
        }
    }


    private fun RBuilder.renderActions() {
        styledDiv {
            css {
                display = Display.inlineBlock
            }

            styledSpan {
                attrs {
                    title = "Shift up"
                }

                child(MaterialIconButton::class) {
                    attrs {
                        onClick = ::onShiftUp
                    }

                    child(KeyboardArrowUpIcon::class) {}
                }
            }

            styledSpan {
                css {
                    marginLeft = (-0.5).em
                }

                attrs {
                    title = "Shift down"
                }

                child(MaterialIconButton::class) {
                    attrs {
                        onClick = ::onShiftDown
                    }

                    child(KeyboardArrowDownIcon::class) {}
                }
            }

            styledSpan {
                css {
                    marginLeft = (-0.5).em
                }

                attrs {
                    title = "Remove"
                }

                child(MaterialIconButton::class) {
                    attrs {
                        onClick = ::onRemove
                    }

                    child(DeleteIcon::class) {}
                }
            }
        }
    }


    private fun RBuilder.renderCard() {
        val objectMetadata = props.structure.graphMetadata.objectMetadata.get(props.objectLocation)

        val reactStyles = reactStyle {
            val cardColor = when (props.state?.phase()) {
                ExecutionPhase.Pending ->
//                    Color("#649fff")
//                    Color.gray
//                    Color.gray.lighten(150)
//                    Color.lightGray
//                    Color.lightGray.darken(5)
                    Color.white

                ExecutionPhase.Running ->
//                    Color.yellow
//                    Color("#ffb82d")
                    Color.gold

                ExecutionPhase.Success ->
//                    Color.green.lighten(50)
                    Color("#00a457")

                ExecutionPhase.Error ->
                    Color.red

                null -> null
            }

            // TODO: format intention to execute
//            if (props.next) {
//                backgroundColor = Color.gold
//            }
//            else
            if (cardColor != null) {
                backgroundColor = cardColor
            }
        }

        child(MaterialCard::class) {
            attrs {
                style = reactStyles
            }

            child(MaterialCardContent::class) {
                renderHeader()

                styledDiv {
                    css {
                        marginBottom = (-1.5).em
                    }

                    for (e in objectMetadata.attributes) {
                        if (e.key == iconAttribute.attribute ||
                                e.key == descriptionAttribute.attribute) {
                            continue
                        }

                        val keyAttributePath = AttributePath.ofAttribute(e.key)

                        val value =
                                props.structure.graphNotation.transitiveAttribute(
                                        props.objectLocation, keyAttributePath)
                                ?: continue

                        styledDiv {
                            css {
                                marginBottom = 0.5.em
                            }

                            renderAttribute(e.key, e.value, value)
                        }
                    }

//                    console.log("^^^^^ props.state - ", props.state)
                    (props.state?.previous as? ExecutionSuccess)?.detail?.let {
//                        if (it is TextExecutionValue) {
//                            img {
//                                attrs {
////                                    width = "256px"
//                                    width = "100%"
//                                    src = "data:image/png;base64,${it.value}"
//                                }
//                            }
//                        }
                        if (it is BinaryExecutionValue) {
//                            println("^^^^^^ it.value - ${it.value.size}")
//                            IoUtils.base64Encode(value)
                            val binary = it

                            val screenshotPngUrl = binary.cache("img") {
                                val base64 = IoUtils.base64Encode(binary.value)
                                "data:png/png;base64,$base64"
                            }

//                            val screenshotPngBase64 = it.asBase64()
                            img {
                                attrs {
                                    width = "100%"
                                    src = screenshotPngUrl
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    private fun RBuilder.renderHeader() {
        val actionDescription = props.structure.graphNotation
                .transitiveAttribute(props.objectLocation, descriptionAttribute)
                ?.asString()
                ?: defaultRunDescription

        val iconWidth = 40.px

        div {
            styledDiv {
                css {
                    display = Display.inlineBlock

                    width = iconWidth
                    height = headerHeight
                }

                if (actionDescription.isNotEmpty()) {
                    attrs {
                        title = actionDescription
                    }
                }

                styledDiv {
                    css {
                        float = Float.left
                        marginTop = (-0.6).em
                        marginLeft = (-1.25).em
                    }

                    renderRunIcon()
                }
            }

            styledDiv {
                css {
                    display = Display.inlineBlock

                    width = 100.pct.minus(iconWidth)
                    height = headerHeight
                }

                styledDiv {
                    css {
                        float = Float.left
                        width = 100.pct

                        marginTop = (-10).px
                    }

                    child(NameEditor::class) {
                        attrs {
                            objectLocation = props.objectLocation
                            notation = props.structure.graphNotation

                            description = actionDescription
                            intentToRun = state.intentToRun

                            runCallback = ::onRun
                        }
                    }
                }
            }
        }
    }


    private fun RBuilder.renderRunIcon() {
        val icon = props.structure.graphNotation
                .transitiveAttribute(props.objectLocation, iconAttribute)
                ?.asString()
                ?: defaultRunIcon

        val highlight =
                if (state.intentToRun && props.state?.phase() != ExecutionPhase.Running) {
                    Color("rgba(255, 215, 0, 0.5)")
//                    Color("rgba(255, 184, 45, 0.5)")
                }
                else {
                    Color("rgba(255, 255, 255, 0.5)")
                }

        child(MaterialIconButton::class) {
            attrs {
                style = reactStyle {
                    marginLeft = 8.px
                    width = 48.px
                    height = 48.px

                    backgroundColor = highlight
                }

                onClick = ::onRun
                onMouseOver = ::onRunEnter
                onMouseOut = ::onRunLeave
            }

            child(iconClassForName(icon)) {
                attrs {
                    style = reactStyle {
                        color = Color.black

                        marginTop = (-9).px
                        fontSize = 1.75.em
                        borderRadius = 20.px

                        backgroundColor = highlight
                    }
                }
            }
        }
    }


    private fun RBuilder.renderAttribute(
            name: AttributeName,
            attributeMetadata: AttributeMetadata,
            attributeValue: AttributeNotation
    ) {
        when (attributeValue) {
            is ScalarAttributeNotation -> {
                val scalarValue =
                        if (attributeMetadata.type?.className == ClassNames.kotlinString) {
                            attributeValue.value.toString()
                        }
                        else {
                            attributeValue.value
                        }

                when (scalarValue) {
                    is String ->
                        child(AttributeEditor::class) {
                            attrs {
                                objectName = props.objectLocation
                                attributeName = name
                                value = scalarValue
                            }
                        }

                    else ->
                        +"[[ ${attributeValue.value} ]]"
                }
            }

            else ->
                +"$attributeValue"
        }
    }
}