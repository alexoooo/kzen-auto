package tech.kzen.auto.client.objects.action

import kotlinx.css.*
import kotlinx.html.js.onMouseOutFunction
import kotlinx.html.js.onMouseOverFunction
import org.w3c.dom.HTMLButtonElement
import react.*
import react.dom.img
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.ExecutionIntent
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.paradigm.imperative.model.BinaryExecutionValue
import tech.kzen.auto.common.paradigm.imperative.model.ExecutionPhase
import tech.kzen.auto.common.paradigm.imperative.model.ExecutionState
import tech.kzen.auto.common.paradigm.imperative.model.ExecutionSuccess
import tech.kzen.lib.common.api.model.AttributeName
import tech.kzen.lib.common.api.model.AttributePath
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.metadata.model.AttributeMetadata
import tech.kzen.lib.common.structure.metadata.model.ObjectMetadata
import tech.kzen.lib.common.structure.notation.edit.RemoveObjectCommand
import tech.kzen.lib.common.structure.notation.edit.ShiftObjectCommand
import tech.kzen.lib.common.structure.notation.model.AttributeNotation
import tech.kzen.lib.common.structure.notation.model.ListAttributeNotation
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
        private val runIconWidth = 40.px
        private val editIconOffset = 12.px
    }


    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var objectLocation: ObjectLocation,
            var structure: GraphStructure,

            var state: ExecutionState?
    ): RProps


    class State(
            var hoverCard: Boolean,
            var hoverMenu: Boolean,
            var intentToRun: Boolean,

            var optionsOpen: Boolean
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    private var editSignal = ObjectNameEditor.EditSignal()
    private var buttonRef: HTMLButtonElement? = null


    //-----------------------------------------------------------------------------------------------------------------
    override fun ActionController.State.init(props: ActionController.Props) {
        hoverCard = false
        hoverMenu = false
        intentToRun = false

        optionsOpen = false
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
        onOptionsClose()

        async {
            ClientContext.commandBus.apply(RemoveObjectCommand(
                    props.objectLocation))
        }
    }


    private fun onShiftUp() {
        onOptionsClose()

        val packagePath = props.objectLocation.documentPath
        val packageNotation = props.structure.graphNotation.documents.values[packagePath]!!
        val index = packageNotation.indexOf(props.objectLocation.objectPath)

        async {
            ClientContext.commandBus.apply(ShiftObjectCommand(
                    props.objectLocation, PositionIndex(index.value - 1)))
        }
    }


    private fun onShiftDown() {
        onOptionsClose()

        val packagePath = props.objectLocation.documentPath
        val packageNotation = props.structure.graphNotation.documents.values[packagePath]!!
        val index = packageNotation.indexOf(props.objectLocation.objectPath)

        async {
            ClientContext.commandBus.apply(ShiftObjectCommand(
                    props.objectLocation, PositionIndex(index.value + 1)))
        }
    }


    private fun onEditName() {
        onOptionsClose()

        editSignal.trigger()
    }


    private fun onMouseOver(cardOrActions: Boolean) {
        if (cardOrActions) {
            setState {
                hoverCard = true
            }
        }
        else {
            setState {
                hoverMenu = true
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
                hoverMenu = false
            }
        }
    }


    private fun onOptionsToggle() {
        setState {
            optionsOpen = ! optionsOpen
        }
    }


    private fun onOptionsClose() {
        setState {
            optionsOpen = false

            hoverCard = false
            hoverMenu = false
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        styledSpan {
            css {
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
    }



    private fun RBuilder.renderCard() {
        val objectMetadata = props.structure.graphMetadata.objectMetadata.get(props.objectLocation)

        val reactStyles = reactStyle {
            val cardColor = when (props.state?.phase()) {
                ExecutionPhase.Pending ->
//                    Color("#649fff")
                    Color.white

                ExecutionPhase.Running ->
                    Color.gold

                ExecutionPhase.Success ->
//                    Color("#00a457")
                    Color("#00b467")
//                    Color("#13aa59")
//                    Color("#1faf61")

                ExecutionPhase.Error ->
                    Color.red

                null -> null
            }

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

                    renderBody(objectMetadata)
                }
            }
        }
    }


    private fun RBuilder.renderHeader() {
        val actionDescription = props.structure.graphNotation
                .transitiveAttribute(props.objectLocation, descriptionAttribute)
                ?.asString()
                ?: defaultRunDescription

        styledDiv {
            css {
                position = Position.relative
                height = headerHeight
                width = 100.pct
            }

            styledDiv {
                css {
                    position = Position.absolute
                    height = headerHeight
                    width = runIconWidth
                    top = (-12).px
                    left = (-20).px
                }

                renderRunIcon(actionDescription)
            }


            styledDiv {
                css {
                    position = Position.absolute
                    height = headerHeight
                    width = 100.pct.minus(runIconWidth).minus(editIconOffset)
                    top = (-11).px
                    left = runIconWidth
                }

                child(ObjectNameEditor::class) {
                    attrs {
                        objectLocation = props.objectLocation
                        notation = props.structure.graphNotation

                        description = actionDescription
                        intentToRun = state.intentToRun

                        runCallback = ::onRun
                        editSignal = this@ActionController.editSignal
                    }
                }
            }


            styledDiv {
                css {
                    position = Position.absolute
                    height = headerHeight
                    width = 23.px
                    top = (-20).px
                    right = 0.px
                }

                renderOptionsMenu()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderBody(objectMetadata: ObjectMetadata) {
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
            if (it is BinaryExecutionValue) {
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


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderOptionsMenu() {
        styledSpan {
            css {
                // NB: blinks in and out without this
                backgroundColor = Color.transparent

                if (! (state.hoverCard || state.hoverMenu)) {
                    display = Display.none
                }
            }

            attrs {
                onMouseOverFunction = {
                    onMouseOver(false)
                }

                onMouseOutFunction = {
                    onMouseOut(false)
                }
            }

            child(MaterialIconButton::class) {
                attrs {
                    title = "Options..."
                    onClick = ::onOptionsToggle

                    buttonRef = {
                        this@ActionController.buttonRef = it
                    }
                }

                child(MoreVertIcon::class) {}
            }
        }

        child(MaterialMenu::class) {
            attrs {
                open = state.optionsOpen

                onClose = ::onOptionsClose

                anchorEl = buttonRef
            }

            renderMenuItems()
        }
    }


    private fun RBuilder.renderMenuItems() {
        val iconStyle = reactStyle {
            marginRight = 1.em
        }

        child(MaterialMenuItem::class) {
            attrs {
                onClick = ::onEditName
            }
            child(EditIcon::class) {
                attrs {
                    style = iconStyle
                }
            }
            +"Rename"
        }

        child(MaterialMenuItem::class) {
            attrs {
                onClick = ::onShiftUp
            }
            child(KeyboardArrowUpIcon::class) {
                attrs {
                    style = iconStyle
                }
            }
            +"Move up"
        }

        child(MaterialMenuItem::class) {
            attrs {
                onClick = ::onShiftDown
            }
            child(KeyboardArrowDownIcon::class) {
                attrs {
                    style = iconStyle
                }
            }
            +"Move down"
        }

        child(MaterialMenuItem::class) {
            attrs {
                onClick = ::onRemove
            }
            child(DeleteIcon::class) {
                attrs {
                    style = iconStyle
                }
            }
            +"Remove"
        }
    }


    private fun RBuilder.renderRunIcon(
            actionDescription: String
    ) {
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
                if (actionDescription.isNotEmpty()) {
                    attrs {
                        title = actionDescription
                    }
                }

                val overfill = 8.px
                style = reactStyle {
                    marginLeft = overfill
                    width = runIconWidth.plus(overfill)
                    height = runIconWidth.plus(overfill)

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
            attributeName: AttributeName,
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
                                objectLocation = props.objectLocation
                                this.attributeName = attributeName
                                value = scalarValue
                            }
                        }

                    else -> {
                        +"[[ ${attributeValue.value} ]]"
                    }
                }
            }

            is ListAttributeNotation -> {
                if (attributeValue.values.all { it.asString() != null }) {
                    val stringValues = attributeValue.values.map { it.asString()!! }

                    child(AttributeEditor::class) {
                        attrs {
                            objectLocation = props.objectLocation
                            this.attributeName = attributeName
                            values = stringValues
                        }
                    }
                }
                else {
                    +"$attributeName: $attributeValue"
                }
            }

            else ->
                +"$attributeValue"
        }
    }
}