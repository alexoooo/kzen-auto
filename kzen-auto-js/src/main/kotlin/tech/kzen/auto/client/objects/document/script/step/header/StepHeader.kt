package tech.kzen.auto.client.objects.document.script.step.header

import emotion.react.css
import js.core.jso
import mui.material.IconButton
import mui.material.Menu
import mui.material.MenuItem
import mui.system.sx
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.*
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeState
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.cqrs.RemoveObjectInAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.ShiftInAttributeCommand
import web.cssom.*
import web.html.HTMLElement
import kotlin.js.Date


//---------------------------------------------------------------------------------------------------------------------
external interface StepHeaderProps : Props {
    var hoverSignal: StepHeader.HoverSignal

    var attributeNesting: AttributeNesting

    var objectLocation: ObjectLocation
    var graphStructure: GraphStructure

    var imperativeState: ImperativeState?
//    var isRunning: Boolean

    var managed: Boolean
    var first: Boolean
    var last: Boolean
}


external interface StepHeaderState: State {
    var hoverCard: Boolean
    var hoverMenu: Boolean
//    var intentToRun: Boolean

    var optionsOpen: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
class StepHeader(
    props: StepHeaderProps
):
    RPureComponent<StepHeaderProps, StepHeaderState>(props)//,
//    ExecutionIntentGlobal.Observer
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
    private var menuAnchorRef: RefObject<HTMLElement> = createRef()

    private var processingOption: Boolean = false
    private var optionCompletedTime: Double? = null


    //-----------------------------------------------------------------------------------------------------------------
    override fun StepHeaderState.init(props: StepHeaderProps) {
        hoverMenu = false
//        intentToRun = false

        optionsOpen = false
    }


    override fun componentDidMount() {
        props.hoverSignal.attach(this)
//        ClientContext.executionIntentGlobal.observe(this)
    }


    override fun componentWillUnmount() {
        props.hoverSignal.detach()
        optionCompletedTime = null
//        ClientContext.executionIntentGlobal.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
//    override fun onExecutionIntent(actionLocation: ObjectLocation?) {
//        setState {
//            intentToRun = actionLocation == props.objectLocation
//        }
//    }


    //-----------------------------------------------------------------------------------------------------------------
//    private fun onRun() {
//        async {
//            ClientContext.executionRepository.execute(
//                    props.objectLocation.documentPath,
//                    props.objectLocation,
//                    props.graphStructure)
//        }
//    }
//
//
//    private fun onRunEnter() {
//        ClientContext.executionIntentGlobal.set(props.objectLocation)
//    }
//
//
//    private fun onRunLeave() {
//        ClientContext.executionIntentGlobal.clearIf(props.objectLocation)
//    }


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
                PositionRelation.at(index + offset)))
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
    override fun ChildrenBuilder.render() {
//        +"${parentObjectLocation}"
//        +"state.intentToRun ${state.intentToRun}"

        val actionDescription = props.graphStructure.graphNotation
                .firstAttribute(props.objectLocation, AutoConventions.descriptionAttributePath)
                ?.asString()
                ?: defaultRunDescription

        div {
            css {
                position = Position.relative
                height = headerHeight
                width = 100.pct
            }

            div {
                css {
                    position = Position.absolute
                    height = headerHeight
                    width = runIconWidth
                    top = (-12).px
                    left = (-20).px
                }

                renderRunIcon(actionDescription)
            }

            div {
                css {
                    position = Position.absolute
                    height = headerHeight
                    width = 100.pct.minus(runIconWidth).minus(menuIconOffset)
                    top = (-13).px
                    left = runIconWidth
                }

                StepNameEditor::class.react {
                    objectLocation = props.objectLocation
                    notation = props.graphStructure.graphNotation

                    description = actionDescription
//                    intentToRun = state.intentToRun

//                    runCallback = ::onRun
                    editSignal = this@StepHeader.editSignal
                }
            }

            div {
                css {
                    position = Position.absolute
                    height = headerHeight
                    width = 23.px
                    top = (-16).px
                    right = 9.px
                }

                ref = this@StepHeader.menuAnchorRef

                renderOptionsMenu()
            }
        }
    }


    private fun ChildrenBuilder.renderRunIcon(
            actionDescription: String
    ) {
        val icon = props.graphStructure.graphNotation
                .firstAttribute(props.objectLocation, AutoConventions.iconAttributePath)
                ?.asString()
                ?: defaultRunIcon

        val highlight =
//                if (state.intentToRun && ! props.isRunning) {
//                    Color("rgba(255, 215, 0, 0.5)")
//                }
//                else {
                    Color("rgba(255, 255, 255, 0.5)")
//                }

        IconButton {
            if (actionDescription.isNotEmpty()) {
                title = actionDescription
            }

            val overfill = 8.px
            sx {
                marginLeft = overfill
                width = runIconWidth.plus(overfill)
                height = runIconWidth.plus(overfill)

                backgroundColor = highlight

                position = Position.relative
            }

//            onClick = { onRun() }
//            onMouseOver = { onRunEnter() }
//            onMouseOut = { onRunLeave() }

            iconClassForName(icon).react {
                style = jso {
                    color = NamedColor.black

                    fontSize = 1.75.em
                    borderRadius = 20.px

                    backgroundColor = highlight

                    margin = Margin(0.em, 0.em, 0.em, 0.em)
                    padding = Padding(0.em, 0.em, 0.em, 0.em)

                    position = Position.absolute
                    top = 3.px
                    left = 3.px
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderOptionsMenu() {
        span {
            css {
                // NB: blinks in and out without this
                backgroundColor = NamedColor.transparent

                if (! (state.hoverCard || state.hoverMenu)) {
                    display = None.none
                }
            }

            onMouseOver = { onMouseOver(false) }
            onMouseOut = { onMouseOut(false) }

            IconButton {
                title = "Options..."
                onClick = { onOptionsOpen() }
                MoreVertIcon::class.react {}
            }
        }

        Menu {
            open = state.optionsOpen
            onClose = ::onOptionsCancel
            anchorEl = menuAnchorRef.current?.let { { _ -> it } }
            renderMenuItems()
        }
    }


    private fun ChildrenBuilder.renderMenuItems() {
        val iconStyle: CSSProperties = jso {
            marginRight = 1.em
        }

        MenuItem {
            onClick = { onEditName() }

            EditIcon::class.react {
                style = iconStyle
            }
            +"Rename"
        }

        if (props.managed) {
            return
        }

        if (! props.first) {
            MenuItem {
                onClick = { onShiftUp() }

                KeyboardArrowUpIcon::class.react {
                    style = iconStyle
                }
                +"Move up"
            }
        }

        if (! props.last) {
            MenuItem {
                onClick = { onShiftDown() }
                KeyboardArrowDownIcon::class.react {
                    style = iconStyle
                }
                +"Move down"
            }
        }

        MenuItem {
            onClick = { onRemove() }

            DeleteIcon::class.react {
                style = iconStyle
            }
            +"Delete"
        }
    }
}