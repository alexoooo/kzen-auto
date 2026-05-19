package tech.kzen.auto.client.objects.document.custom

import emotion.react.css
import mui.material.CardContent
import mui.material.Chip
import mui.material.ChipVariant
import mui.material.IconButton
import mui.material.Paper
import mui.material.Size
import mui.material.ToggleButton
import mui.system.sx
import react.ChildrenBuilder
import react.Props
import react.ReactNode
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.document.common.edit.ObjectNameEditor
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.DeleteIcon
import tech.kzen.auto.client.wrap.material.EditIcon
import tech.kzen.auto.client.wrap.material.OpenInNewIcon
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.custom.CustomConventions
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.metadata.ObjectMetadata
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface CustomObjectProps: Props {
    var objectPath: ObjectPath
    var objectLocation: ObjectLocation
    var objectMetadata: ObjectMetadata?
    var isAbstract: Boolean
    var isLogic: Boolean
    var isExported: Boolean
    var onToggleExport: (() -> Unit)?
    var onDelete: () -> Unit
    var attributeEditorManager: AttributeEditorManager.Wrapper
}


external interface CustomObjectState: State {
    var editing: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class CustomObject(
    props: CustomObjectProps
):
    RPureComponent<CustomObjectProps, CustomObjectState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun CustomObjectState.init(props: CustomObjectProps) {
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


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        Paper {
            sx {
                if (props.isAbstract) {
                    backgroundColor = Color("rgb(240, 244, 250)")
                    borderStyle = LineStyle.dashed
                    borderWidth = 1.px
                    borderColor = Color("rgb(160, 175, 200)")
                }
                else {
                    backgroundColor = NamedColor.white
                }
            }

            CardContent {
                div {
                    css {
                        display = Display.flex
                        alignItems = AlignItems.center
                        marginBottom = 0.75.em
                    }

                    if (state.editing) {
                        renderNameEditor()
                    }
                    else {
                        renderNameReader()
                    }
                }

                val objectMetadata = props.objectMetadata
                if (objectMetadata == null) {
                    div {
                        css {
                            fontStyle = FontStyle.italic
                            color = Color("rgb(128, 80, 0)")
                        }
                        +"(metadata unavailable)"
                    }
                }
                else {
                    renderAttributes(objectMetadata)
                }
            }
        }
    }


    private fun ChildrenBuilder.renderNameReader() {
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

            css {
                marginLeft = 0.25.em
            }

            onClick = {
                onStartEdit()
            }

            EditIcon::class.react {}
        }

        IconButton {
            title = "Delete"
            size = Size.small

            sx {
                marginLeft = Auto.auto
            }

            onClick = {
                props.onDelete()
            }

            DeleteIcon::class.react {}
        }

        if (props.isAbstract) {
            Chip {
                css {
                    marginLeft = 0.5.em
                }
                size = Size.small
                label = ReactNode("abstract")
                variant = ChipVariant.outlined
            }
        }

        if (props.isLogic) {
            Chip {
                css {
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

                OpenInNewIcon::class.react {}
            }
        }
    }


    private fun ChildrenBuilder.renderNameEditor() {
        ObjectNameEditor::class.react {
            objectLocation = props.objectLocation
            onClose = ::onCloseEdit
        }
    }


    private fun ChildrenBuilder.renderAttributes(objectMetadata: ObjectMetadata) {
        for (entry in objectMetadata.attributes.map) {
            val attributeName = entry.key
            if (CustomConventions.isManaged(attributeName)) {
                continue
            }

            div {
                css {
                    marginBottom = 0.5.em
                }

                props.attributeEditorManager.child(this) {
                    this.objectLocation = props.objectLocation
                    this.attributeName = attributeName
                }
            }
        }
    }
}
