package tech.kzen.auto.client.objects.document.custom.view.obj

import emotion.react.css
import mui.material.*
import mui.material.Size
import mui.system.sx
import react.ChildrenBuilder
import react.Props
import react.ReactNode
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.objects.document.common.edit.ObjectNameEditor
import tech.kzen.auto.client.objects.document.custom.CustomTheme
import tech.kzen.auto.client.objects.document.custom.view.CustomViewStore
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.iconByName
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.model.location.ObjectLocation
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface CustomObjectHeaderProps: Props {
    var objectLocation: ObjectLocation
    var info: CustomObjectInfo
    var viewStore: CustomViewStore
    var headerExtra: ((ChildrenBuilder) -> Unit)?
}


external interface CustomObjectHeaderState: State {
    var editing: Boolean
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


    private fun onToggleExport() {
        props.viewStore.toggleExport(props.objectLocation)
    }


    private fun onDelete() {
        props.viewStore.deleteObject(props.objectLocation)
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
            // NB: rename button revealed on hover via CSS (data-rename-button below), not a hover state field —
            //     a state toggle would re-reconcile sibling objects on every mouse move and flash them in React
            //     DevTools' "Highlight updates" overlay (a false positive).
            css {
                flexGrow = number(1.0)
                minWidth = 0.px
                display = Display.flex
                alignItems = AlignItems.center

                "&:hover [data-rename-button]" {
                    opacity = number(1.0)
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
            +props.objectLocation.objectPath.name.value
        }

        // NB: wrapper carries data-rename-button + base hidden opacity; the name area's &:hover rule reveals it.
        div {
            asDynamic()["data-rename-button"] = ""

            css {
                display = Display.inlineFlex
                alignItems = AlignItems.center
                marginLeft = 0.25.em
                opacity = number(0.0)
            }

            IconButton {
                title = "Rename"
                size = Size.small

                onClick = { onStartEdit() }

                iconByName("Edit") {}
            }
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

            if (props.info.isAbstract) {
                Chip {
                    sx {
                        marginLeft = 0.5.em
                    }
                    size = Size.small
                    label = ReactNode("abstract")
                    variant = ChipVariant.outlined
                }
            }

            if (props.info.isLogic) {
                Chip {
                    sx {
                        marginLeft = 0.5.em
                    }
                    size = Size.small
                    label = ReactNode("logic")
                    variant = ChipVariant.outlined
                }
            }

            props.headerExtra?.invoke(this@renderRightCluster)

            if (!props.info.isAbstract) {
                ToggleButton {
                    sx {
                        marginLeft = 0.5.em
                        height = 24.px
                        paddingTop = 0.px
                        paddingBottom = 0.px
                        if (props.info.isExported) {
                            color = CustomTheme.exportAccent
                            borderColor = CustomTheme.exportGlow
                            filter = dropShadow(0.px, 0.px, 3.px, CustomTheme.exportGlow)
                        }
                    }
                    value = "export"
                    size = Size.small
                    selected = props.info.isExported
                    onChange = { _, _ -> onToggleExport() }
                    title = if (props.info.isExported) "Exported (click to unexport)" else "Mark as exported"

                    iconByName("Public") {}
                }
            }

            IconButton {
                title = "Delete"
                size = Size.small

                sx {
                    marginLeft = 0.5.em
                }

                onClick = { onDelete() }

                iconByName("Delete") {}
            }
        }
    }
}
