package tech.kzen.auto.client.objects.document.script.action

import kotlinx.css.*
import kotlinx.html.js.onMouseOutFunction
import kotlinx.html.js.onMouseOverFunction
import org.w3c.dom.HTMLButtonElement
import react.RBuilder
import react.RProps
import react.RState
import react.dom.img
import react.setState
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.objects.document.common.AttributeController
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.ExecutionIntent
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.objects.document.script.ScriptDocument
import tech.kzen.auto.common.paradigm.common.model.BinaryExecutionValue
import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.auto.common.paradigm.common.model.ScalarExecutionValue
import tech.kzen.auto.common.paradigm.imperative.model.ImperativePhase
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeState
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeSuccess
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.metadata.model.ObjectMetadata
import tech.kzen.lib.common.structure.notation.NotationConventions
import tech.kzen.lib.common.structure.notation.edit.RemoveObjectInAttributeCommand
import tech.kzen.lib.common.structure.notation.edit.ShiftInAttributeCommand
import tech.kzen.lib.common.structure.notation.model.PositionIndex
import tech.kzen.lib.platform.IoUtils
import kotlin.js.Date


class ActionController(
        props: Props
):
        RPureComponent<ActionController.Props, ActionController.State>(props),
        ExecutionIntent.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        const val defaultRunIcon = "PlayArrowIcon"
        const val defaultRunDescription = "Run"

        val headerHeight = 2.5.em
        private val runIconWidth = 40.px
        private val menuIconOffset = 12.px

        private const val menuDanglingTimeout = 300
    }


    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var attributeController: AttributeController.Wrapper,

            var attributeNesting: AttributeNesting,
            var objectLocation: ObjectLocation,
            var graphStructure: GraphStructure,

            var state: ImperativeState?
    ): RProps


    class State(
            var hoverCard: Boolean,
            var hoverMenu: Boolean,
            var intentToRun: Boolean,

            var optionsOpen: Boolean
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    private var editSignal = ActionNameEditor.EditSignal()
    private var buttonRef: HTMLButtonElement? = null

    private var processingOption: Boolean = false
    private var optionCompletedTime: Double? = null


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
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
            prevProps: Props,
            prevState: State,
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
            ClientContext.executionManager.execute(
                    props.objectLocation.documentPath,
                    props.objectLocation)
        }
    }


    private fun onRunEnter() {
        ClientContext.executionIntent.set(props.objectLocation)
    }


    private fun onRunLeave() {
        ClientContext.executionIntent.clearIf(props.objectLocation)
    }


    private fun onRemove() {
        performOption {
            val scriptMain = ObjectLocation(
                    props.objectLocation.documentPath,
                    NotationConventions.mainObjectPath)

            val objectAttributePath = AttributePath(
                    ScriptDocument.stepsAttributePath.attribute,
                    props.attributeNesting)

            ClientContext.commandBus.apply(RemoveObjectInAttributeCommand(
                    scriptMain, objectAttributePath))
        }
    }


    private fun onShiftUp() {
        onShift(-1)
    }


    private fun onShiftDown() {
        onShift(1)
    }


    private fun onShift(offset: Int) {
        performOption {
            // NB: makes onOptionsClose take effect faster
//            delay(1)

            val scriptMain = ObjectLocation(
                    props.objectLocation.documentPath,
                    NotationConventions.mainObjectPath)

            val objectAttributePath = AttributePath(
                    ScriptDocument.stepsAttributePath.attribute,
                    props.attributeNesting)

            val index = props.attributeNesting.segments.last().asIndex()!!
//            console.log("^^^^ onShift", index, offset, props.attributeNesting)

            ClientContext.commandBus.apply(ShiftInAttributeCommand(
                    scriptMain,
                    objectAttributePath,
                    PositionIndex(index + offset)))
        }
    }


    private fun onEditName() {
        performOption {
            editSignal.trigger()
        }
    }


    private fun onMouseOver(cardOrActions: Boolean) {
        if (state.optionsOpen || processingOption) {
//            console.log("^^^ onMouseOver hoverItem - skip due to optionsOpen")
            return
        }

        optionCompletedTime?.let {
            val now = Date.now()
            val elapsed = now - it
//            console.log("^^^ onMouseOver hoverItem - elapsed", elapsed)

            if (elapsed < menuDanglingTimeout) {
                return
            }
            else {
                optionCompletedTime = null
            }
        }

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


    private fun onOptionsOpen() {
        setState {
            optionsOpen = true
        }
    }


    private fun onOptionsClose() {
        setState {
            optionsOpen = false
            hoverCard = false
            hoverMenu = false
        }
    }


    private fun onOptionsCancel() {
//        console.log("^^^^^^ onOptionsCancel")
        onOptionsClose()
        optionCompletedTime = Date.now()
    }


    private fun performOption(action: suspend () -> Unit) {
        processingOption = true
        onOptionsClose()

        async {
            action.invoke()
        }.then {
            optionCompletedTime = Date.now()
            processingOption = false
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
        val objectMetadata = props.graphStructure.graphMetadata.objectMetadata[props.objectLocation]!!

        val reactStyles = reactStyle {
            val cardColor = when (props.state?.phase()) {
                ImperativePhase.Pending ->
//                    Color("#649fff")
                    Color.white

                ImperativePhase.Running ->
                    Color.gold

                ImperativePhase.Success ->
//                    Color("#00a457")
                    Color("#00b467")
//                    Color("#13aa59")
//                    Color("#1faf61")

                ImperativePhase.Error ->
                    Color.red

                null ->
//                    null
                    Color.gray
            }

//            if (cardColor != null) {
                backgroundColor = cardColor
//            }
        }

//        child(MaterialCard::class) {
        child(MaterialPaper::class) {
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
        val actionDescription = props.graphStructure.graphNotation
                .transitiveAttribute(props.objectLocation, AutoConventions.descriptionAttributePath)
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
                    width = 100.pct.minus(runIconWidth).minus(menuIconOffset)
                    top = (-13).px
                    left = runIconWidth
                }

                child(ActionNameEditor::class) {
                    attrs {
                        objectLocation = props.objectLocation
                        notation = props.graphStructure.graphNotation

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

//                    top = (-20).px
//                    top = (-15).px
                    top = (-16).px

//                    right = 0.px
                    right = 9.px
                }

                renderOptionsMenu()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderBody(objectMetadata: ObjectMetadata) {
        for (e in objectMetadata.attributes.values) {
            if (AutoConventions.isManaged(e.key)) {
                continue
            }

//            val keyAttributePath = AttributePath.ofName(e.key)

//            val attributeNotation = props.graphStructure.graphNotation.transitiveAttribute(
//                    props.objectLocation, keyAttributePath)

            styledDiv {
                css {
                    marginBottom = 0.5.em
                }

                renderAttribute(e.key/*, e.value, attributeNotation*/)
            }
        }

//                    console.log("^^^^^ props.state - ", props.state)
        (props.state?.previous as? ImperativeSuccess)?.detail?.let {
            renderDetail(it)
        }
    }


    private fun RBuilder.renderDetail(detail: ExecutionValue) {
        if (detail is BinaryExecutionValue) {

            val screenshotPngUrl = detail.cache("img") {
                val base64 = IoUtils.base64Encode(detail.value)
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
        else if (detail is ScalarExecutionValue) {
            +"${detail.get()}"
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
                    onClick = ::onOptionsOpen

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

                onClose = ::onOptionsCancel

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
            +"Delete"
        }
    }


    private fun RBuilder.renderRunIcon(
            actionDescription: String
    ) {
        val icon = props.graphStructure.graphNotation
                .transitiveAttribute(props.objectLocation, AutoConventions.iconAttributePath)
                ?.asString()
                ?: defaultRunIcon

        val highlight =
                if (state.intentToRun && props.state?.phase() != ImperativePhase.Running) {
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
            attributeName: AttributeName//,
//            attributeMetadata: AttributeMetadata,
//            attributeNotation: AttributeNotation?
    ) {
        props.attributeController.child(this) {
            attrs {
                this.graphStructure = props.graphStructure
                this.objectLocation = props.objectLocation
                this.attributeName = attributeName
//                this.attributeMetadata = attributeMetadata
//                this.attributeNotation = attributeNotation
            }
        }
    }
}