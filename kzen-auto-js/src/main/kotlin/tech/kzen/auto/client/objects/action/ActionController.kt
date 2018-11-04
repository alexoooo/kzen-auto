package tech.kzen.auto.client.objects.action

import kotlinx.css.*
import kotlinx.html.js.onMouseOutFunction
import kotlinx.html.js.onMouseOverFunction
import kotlinx.html.title
import react.*
import react.dom.div
import react.dom.span
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.exec.ExecutionStatus
import tech.kzen.lib.common.edit.RemoveObjectCommand
import tech.kzen.lib.common.edit.ShiftObjectCommand
import tech.kzen.lib.common.metadata.model.GraphMetadata
import tech.kzen.lib.common.metadata.model.ParameterMetadata
import tech.kzen.lib.common.notation.model.ParameterNotation
import tech.kzen.lib.common.notation.model.ProjectNotation
import tech.kzen.lib.common.notation.model.ScalarParameterNotation
import tech.kzen.lib.platform.ClassNames


class ActionController(
        props: ActionController.Props
):
        RComponent<ActionController.Props, ActionController.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        const val iconParameter = "icon"
        const val descriptionParameter = "description"

        const val defaultRunIcon = "PlayArrowIcon"
        const val defaultRunDescription = "Run"

        val headerHeight = 2.5.em
    }


    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var name: String,
            var notation: ProjectNotation,
            var metadata: GraphMetadata,

            var status: ExecutionStatus?,
            var next: Boolean
    ): RProps


    class State(
            var hoverCard: Boolean = false,
            var hoverAction: Boolean = false
    ): RState


    @Suppress("unused")
    class Wrapper: ActionWrapper {
        override fun priority(): Int {
            return 0
        }


        override fun isApplicableTo(
                objectName: String,
                projectNotation: ProjectNotation,
                graphMetadata: GraphMetadata
        ): Boolean {
            return true
        }


        override fun render(
                rBuilder: RBuilder,
                objectName: String,
                projectNotation: ProjectNotation,
                graphMetadata: GraphMetadata,
                executionStatus: ExecutionStatus?,
                nextToExecute: Boolean
        ): ReactElement {
            return rBuilder.child(ActionController::class) {
                attrs {
                    name = objectName

                    notation = projectNotation
                    metadata = graphMetadata

                    status = executionStatus
                    next = nextToExecute
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ActionController.State.init(props: ActionController.Props) {
        hoverCard = false
        hoverAction = false
    }


    override fun componentDidUpdate(prevProps: ActionController.Props, prevState: ActionController.State, snapshot: Any) {
//        console.log("state.hoverCard: ${state.hoverCard} | state.hoverAction: ${state.hoverAction}")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onRun() {
        async {
            ClientContext.executionManager.execute(props.name)
        }
    }


    private fun onRemove() {
        async {
            ClientContext.commandBus.apply(RemoveObjectCommand(
                    props.name))
        }
    }


    private fun onShiftUp() {
        val packagePath = props.notation.findPackage(props.name)
        val packageNotation = props.notation.packages[packagePath]!!
        val index = packageNotation.indexOf(props.name)

        async {
            ClientContext.commandBus.apply(ShiftObjectCommand(
                    props.name, index - 1))
        }
    }


    private fun onShiftDown() {
        val packagePath = props.notation.findPackage(props.name)
        val packageNotation = props.notation.packages[packagePath]!!
        val index = packageNotation.indexOf(props.name)

        async {
            ClientContext.commandBus.apply(ShiftObjectCommand(
                    props.name, index + 1))
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
        val objectMetadata = props.metadata.objectMetadata[props.name]!!

        val reactStyles = reactStyle {
            val statusColor = when (props.status) {
                ExecutionStatus.Pending ->
                    Color.blue.lighten(75)

                ExecutionStatus.Running ->
                    Color.yellow

                ExecutionStatus.Success ->
                    Color.green.lighten(50)

                ExecutionStatus.Failed ->
                    Color.red

                null -> null
            }

            if (props.next) {
                backgroundColor = Color.gold
            }
            else if (statusColor != null) {
                backgroundColor = statusColor
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

                    for (e in objectMetadata.parameters) {
                        if (e.key == iconParameter ||
                                e.key == descriptionParameter) {
                            continue
                        }

                        val value =
                                props.notation.transitiveParameter(props.name, e.key)
                                ?: continue

                        styledDiv {
                            css {
                                marginBottom = 0.5.em
                            }

                            renderParameter(e.key, e.value, value)
                        }
                    }
                }
            }
        }
    }


    private fun RBuilder.renderHeader() {
        val description = props.notation
                .transitiveParameter(props.name, descriptionParameter)
                ?.asString()
                ?: defaultRunDescription

        val iconWidth = 2.em

        div {
            styledDiv {
                css {
                    display = Display.inlineBlock

                    width = iconWidth
                    height = headerHeight
                }

                if (description.isNotEmpty()) {
                    attrs {
                        title = description
                    }
                }

                styledDiv {
                    css {
                        float = Float.left
                        marginTop = (-0.5).em
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
                    }

                    child(NameEditor::class) {
                        attrs {
                            objectName = props.name
                            notation = props.notation
                        }
                    }
                }
            }
        }
    }


    private fun RBuilder.renderRunIcon() {
        val icon = props.notation
                .transitiveParameter(props.name, iconParameter)
                ?.asString()
                ?: defaultRunIcon

        child(MaterialIconButton::class) {
            attrs {
                style = reactStyle {
                    backgroundColor = Color("rgba(255, 255, 255, 0.5)")
                }

                onClick = ::onRun
            }

            child(iconClassForName(icon)) {
                attrs {
                    style = reactStyle {
                        color = Color.black

                        fontSize = 1.75.em
                        borderRadius = 20.px
                        backgroundColor =  Color("rgba(255, 255, 255, 0.5)")
                    }
                }
            }
        }
    }


    private fun RBuilder.renderParameter(
            parameterName: String,
            parameterMetadata: ParameterMetadata,
            parameterValue: ParameterNotation
    ) {
        when (parameterValue) {
            is ScalarParameterNotation -> {
                val scalarValue =
                        if (parameterMetadata.type?.className == ClassNames.kotlinString) {
                            parameterValue.value.toString()
                        }
                        else {
                            parameterValue.value
                        }

                when (scalarValue) {
                    is String ->
                        child(ParameterEditor::class) {
                            attrs {
                                objectName = props.name
                                parameterPath = parameterName
                                value = scalarValue
                            }
                        }

                    else ->
                        +"[[ ${parameterValue.value} ]]"
                }
            }

            else ->
                +"$parameterValue"
        }
    }
}