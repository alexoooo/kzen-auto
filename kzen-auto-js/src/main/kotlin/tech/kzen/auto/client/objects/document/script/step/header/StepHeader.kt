package tech.kzen.auto.client.objects.document.script.step.header

import kotlinx.css.*
import kotlinx.html.js.onMouseOutFunction
import kotlinx.html.js.onMouseOverFunction
import org.w3c.dom.HTMLElement
import react.RBuilder
import react.RProps
import react.RState
import react.setState
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.ExecutionIntent
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.paradigm.imperative.model.ImperativePhase
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeState
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.PositionIndex
import tech.kzen.lib.common.model.structure.notation.cqrs.RemoveObjectInAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.ShiftInAttributeCommand
import kotlin.js.Date


class StepHeader(
        props: Props
):
        RPureComponent<StepHeader.Props, StepHeader.State>(props),
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
            var hoverSignal: HoverSignal,

            var attributeNesting: AttributeNesting,

            var objectLocation: ObjectLocation,
            var graphStructure: GraphStructure,

            var imperativeState: ImperativeState?
    ): RProps


    class State(
            var hoverCard: Boolean,
            var hoverMenu: Boolean,
            var intentToRun: Boolean,

            var optionsOpen: Boolean
    ): RState


    class HoverSignal {
        private var callback: StepHeader? = null


        fun triggerMouseOver() {
            check(callback != null)
            callback!!.onMouseOver(true)
        }


        fun triggerMouseOut() {
            check(callback != null)
            callback!!.onMouseOut(true)
        }


        fun attach(callback: StepHeader) {
            check(this.callback == null)
            this.callback = callback
        }


        fun detach() {
            this.callback = null
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var editSignal = StepNameEditor.EditSignal()
    private var buttonRef: HTMLElement? = null

    private var processingOption: Boolean = false
    private var optionCompletedTime: Double? = null


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        hoverMenu = false
        intentToRun = false

        optionsOpen = false
    }


    override fun componentDidMount() {
        props.hoverSignal.attach(this)
        ClientContext.executionIntent.observe(this)
    }


    override fun componentWillUnmount() {
        props.hoverSignal.detach()
        optionCompletedTime = null
        ClientContext.executionIntent.unobserve(this)
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


    private fun onMouseOver(cardOrActions: Boolean) {
        if (state.optionsOpen || processingOption) {
//            console.log("^^^ onMouseOver hoverItem - skip due to optionsOpen")
            return
        }

        // TODO: bring back this workaround
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
            hoverMenu = false
        }
    }


    private fun onOptionsCancel() {
//        console.log("^^^^^^ onOptionsCancel")
        onOptionsClose()
        optionCompletedTime = Date.now()
    }


    private fun onRemove() {
        performOption {
            val containingObjectLocation = props.objectLocation.parent()!!
            val objectAttributePath = attributePathInContainer()

            ClientContext.mirroredGraphStore.apply(RemoveObjectInAttributeCommand(
                    containingObjectLocation, objectAttributePath))
        }
    }


    private fun onEditName() {
        performOption {
            editSignal.trigger()
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

            val containingObjectLocation = props.objectLocation.parent()!!
            val objectAttributePath = attributePathInContainer()

//            val index = props.attributeNesting.segments.last().asIndex()!!
            val index =
//                    props.attributePath.nesting.segments.last().asIndex()!!
                    props.attributeNesting.segments.last().asIndex()!!
//            console.log("^^^^ onShift", index, offset, props.attributeNesting)

            ClientContext.mirroredGraphStore.apply(ShiftInAttributeCommand(
                    containingObjectLocation,
                    objectAttributePath,
//                    props.attributePath,
                    PositionIndex(index + offset)))
        }
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


    private fun attributePathInContainer(): AttributePath {
        val containingAttribute = props.objectLocation.objectPath.nesting.segments.last().attributePath
        return AttributePath(
                containingAttribute.attribute,
                containingAttribute.nesting.push(props.attributeNesting))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
//        +"${parentObjectLocation}"
//        +"state.intentToRun ${state.intentToRun}"

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

                child(StepNameEditor::class) {
                    attrs {
                        objectLocation = props.objectLocation
                        notation = props.graphStructure.graphNotation

                        description = actionDescription
                        intentToRun = state.intentToRun

                        runCallback = ::onRun
                        editSignal = this@StepHeader.editSignal
                    }
                }
            }

            styledDiv {
                css {
                    position = Position.absolute
                    height = headerHeight
                    width = 23.px
                    top = (-16).px
                    right = 9.px
                }

                renderOptionsMenu()
            }
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
                if (state.intentToRun && props.imperativeState?.phase() != ImperativePhase.Running) {
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

                    position = Position.relative
                }

                onClick = ::onRun
                onMouseOver = ::onRunEnter
                onMouseOut = ::onRunLeave
            }

//            styledDiv {
//                css {
//                    margin(0.em)
//                    padding(0.em)
//                }
//                +"x"
//            }
            child(iconClassForName(icon)) {
                attrs {
                    style = reactStyle {
                        color = Color.black

//                        marginTop = (-9).px
                        fontSize = 1.75.em
                        borderRadius = 20.px

                        backgroundColor = highlight

                        margin(0.em)
                        padding(0.em)

                        position = Position.absolute
                        top = 3.px
                        left = 3.px
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
                    onClick = ::onOptionsOpen

                    buttonRef = {
                        this@StepHeader.buttonRef = it
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
}