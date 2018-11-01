package tech.kzen.auto.client.objects.action

import kotlinx.css.*
import kotlinx.html.title
import react.*
import react.dom.div
import react.dom.span
import styled.css
import styled.styledDiv
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
) :
        RComponent<ActionController.Props, RState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        const val iconParameter = "icon"
        const val descriptionParameter = "description"

        val headerHeight = 2.5.em
    }


    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var name: String,
            var notation: ProjectNotation,
            var metadata: GraphMetadata,

            var status: ExecutionStatus?,
            var next: Boolean
    ) : RProps


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


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val objectMetadata = props.metadata.objectMetadata[props.name]!!

        val reactStyles = reactStyle {
            val statusColor = when (props.status) {
                ExecutionStatus.Pending ->
                    Color("rgb(225, 225, 225)")

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


            child(MaterialCardActions::class) {
                span {
                    attrs {
                        title = "Run"
                    }

                    child(MaterialIconButton::class) {
                        attrs {
                            onClick = ::onRun
                        }

                        child(PlayArrowIcon::class) {}
                    }
                }


                span {
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


                span {
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


                span {
                    attrs {
                        title = "Remove"
                    }

                    child(MaterialIconButton::class) {
                        attrs {
//                            variant = "outlined"
//                            size = "small"

                            onClick = ::onRemove
                        }

                        child(DeleteIcon::class) {}
                    }
                }
            }
        }
    }


    private fun RBuilder.renderHeader() {
        val icon = props.notation.transitiveParameter(props.name, iconParameter)?.asString()
        val description = props.notation.transitiveParameter(props.name, descriptionParameter)?.asString() ?: ""

        val iconWidth = 2.em

        div {
            styledDiv {
                css {
                    display = Display.inlineBlock

//                    marginTop = (-1).em
                    width = iconWidth
                    height = headerHeight

//                    backgroundColor = Color.mediumPurple
                }

                if (description.isNotEmpty()) {
                    attrs {
                        title = description
                    }
                }

                if (icon != null) {
                    styledDiv {
                        css {
                            float = Float.left
                        }

                        iconByName(this, icon)
                    }
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