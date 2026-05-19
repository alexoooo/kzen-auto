package tech.kzen.auto.client.objects.document.custom

import emotion.react.css
import mui.material.Chip
import mui.material.ChipVariant
import mui.material.IconButton
import mui.material.Size
import mui.material.ToggleButton
import mui.system.sx
import react.ChildrenBuilder
import react.Props
import react.ReactNode
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.objects.document.common.edit.ObjectNameEditor
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.DeleteIcon
import tech.kzen.auto.client.wrap.material.EditIcon
import tech.kzen.auto.client.wrap.material.PublicIcon
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface CustomObjectHeaderProps: Props {
    var objectPath: ObjectPath
    var objectLocation: ObjectLocation
    var isAbstract: Boolean
    var isLogic: Boolean
    var isExported: Boolean
    var onToggleExport: (() -> Unit)?
    var onDelete: () -> Unit
}


external interface CustomObjectHeaderState: State {
    var editing: Boolean
    var nameHovered: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class CustomObjectHeader(
    props: CustomObjectHeaderProps
):
    RPureComponent<CustomObjectHeaderProps, CustomObjectHeaderState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun CustomObjectHeaderState.init(props: CustomObjectHeaderProps) {
        editing = false
        nameHovered = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onStartEdit() {
        setState {
            editing = true
        }
    }


    private fun onCloseEdit() {
        setState {
            editing = false
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            css {
                display = Display.flex
                alignItems = AlignItems.center
                marginBottom = 0.75.em
            }

            renderNameArea()
            renderRightCluster()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderNameArea() {
        div {
            css {
                flexGrow = number(1.0)
                minWidth = 0.px
                display = Display.flex
                alignItems = AlignItems.center
            }

            onMouseEnter = {
                if (! state.nameHovered) {
                    setState {
                        nameHovered = true
                    }
                }
            }

            onMouseLeave = {
                if (state.nameHovered) {
                    setState {
                        nameHovered = false
                    }
                }
            }

            if (state.editing) {
                renderEditor()
            }
            else {
                renderReader()
            }
        }
    }


    private fun ChildrenBuilder.renderReader() {
        span {
            css {
                fontWeight = FontWeight.bold
                fontSize = 1.1.em
            }
            +props.objectPath.name.value
        }

        IconButton {
            title = "Rename"
            size = Size.small

            sx {
                marginLeft = 0.25.em
                opacity = if (state.nameHovered) number(1.0) else number(0.0)
            }

            onClick = {
                onStartEdit()
            }

            EditIcon::class.react {}
        }
    }


    private fun ChildrenBuilder.renderEditor() {
        ObjectNameEditor::class.react {
            objectLocation = props.objectLocation
            onClose = ::onCloseEdit
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderRightCluster() {
        div {
            css {
                display = Display.flex
                alignItems = AlignItems.center
            }

            if (props.isAbstract) {
                Chip {
                    sx {
                        marginLeft = 0.5.em
                    }
                    size = Size.small
                    label = ReactNode("abstract")
                    variant = ChipVariant.outlined
                }
            }

            if (props.isLogic) {
                Chip {
                    sx {
                        marginLeft = 0.5.em
                    }
                    size = Size.small
                    label = ReactNode("logic")
                    variant = ChipVariant.outlined
                }
            }

            val exportHandler = props.onToggleExport
            if (exportHandler != null) {
                ToggleButton {
                    sx {
                        marginLeft = 0.5.em
                        height = 24.px
                        paddingTop = 0.px
                        paddingBottom = 0.px
                    }
                    value = "export"
                    size = Size.small
                    selected = props.isExported
                    onChange = { _, _ -> exportHandler() }
                    title = if (props.isExported) "Exported (click to unexport)" else "Mark as exported"

                    PublicIcon::class.react {}
                }
            }
            else if (props.isAbstract) {
                ToggleButton {
                    sx {
                        marginLeft = 0.5.em
                        height = 24.px
                        paddingTop = 0.px
                        paddingBottom = 0.px
                        visibility = Visibility.hidden
                    }
                    value = "export"
                    size = Size.small
                    disabled = true

                    PublicIcon::class.react {}
                }
            }

            IconButton {
                title = "Delete"
                size = Size.small

                sx {
                    marginLeft = 0.5.em
                }

                onClick = {
                    props.onDelete()
                }

                DeleteIcon::class.react {}
            }
        }
    }
}
