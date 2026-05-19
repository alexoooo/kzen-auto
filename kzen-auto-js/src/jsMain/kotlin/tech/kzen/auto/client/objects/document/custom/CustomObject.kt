package tech.kzen.auto.client.objects.document.custom

import emotion.react.css
import mui.material.CardContent
import mui.material.Paper
import mui.system.sx
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.DragIndicatorIcon
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.custom.CustomConventions
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.metadata.ObjectMetadata
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
enum class DropMarker {
    Above,
    Below
}


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

    var indexInDocument: Int
    var dropMarker: DropMarker?
    var onDragStart: (Int) -> Unit
    var onDragOver: (Int, Boolean) -> Unit
    var onDragEnd: () -> Unit
    var onDrop: () -> Unit
}


external interface CustomObjectState: State {
    var isHovering: Boolean
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
        isHovering = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            css {
                position = Position.relative
                marginBottom = 1.em
            }

            onMouseEnter = {
                if (!state.isHovering) {
                    setState { isHovering = true }
                }
            }
            onMouseLeave = {
                if (state.isHovering) {
                    setState { isHovering = false }
                }
            }
            onDragOver = { event ->
                event.preventDefault()
                val rect = event.currentTarget.getBoundingClientRect()
                val dropAfter = event.clientY > rect.top + rect.height / 2
                props.onDragOver(props.indexInDocument, dropAfter)
            }
            onDrop = { event ->
                event.preventDefault()
                props.onDrop()
            }

            renderHandle()
            renderDropIndicator()

            Paper {
                sx {
                    if (props.isAbstract) {
                        backgroundColor = Color("rgb(244, 244, 246)")
                        borderStyle = LineStyle.dashed
                        borderWidth = 1.px
                        borderColor = Color("rgb(175, 175, 180)")
                        color = Color("rgb(110, 110, 115)")
                    }
                    else {
                        backgroundColor = NamedColor.white
                    }

                    if (props.isExported) {
                        filter = dropShadow(0.px, 0.px, 4.px, Color("rgba(255, 193, 7, 0.55)"))
                    }
                }

                CardContent {
                    CustomObjectHeader::class.react {
                        objectPath = props.objectPath
                        objectLocation = props.objectLocation
                        isAbstract = props.isAbstract
                        isLogic = props.isLogic
                        isExported = props.isExported
                        onToggleExport = props.onToggleExport
                        onDelete = props.onDelete
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
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderHandle() {
        div {
            css {
                position = Position.absolute
                top = 0.px
                bottom = 0.px
                left = (-1.25).em
                width = 1.25.em
                display = Display.flex
                alignItems = AlignItems.center
                justifyContent = JustifyContent.center
                cursor = Cursor.grab
                color = Color("rgb(110, 110, 115)")
                opacity =
                    if (state.isHovering || props.dropMarker != null) number(1.0)
                    else number(0.0)
            }

            draggable = true
            onDragStart = { event ->
                event.dataTransfer.setData("text/plain", "")
                props.onDragStart(props.indexInDocument)
            }
            onDragEnd = {
                props.onDragEnd()
            }

            DragIndicatorIcon::class.react {}
        }
    }


    private fun ChildrenBuilder.renderDropIndicator() {
        val marker = props.dropMarker ?: return
        div {
            css {
                position = Position.absolute
                left = 0.px
                right = 0.px
                height = 2.px
                backgroundColor = Color("#649fff")
                pointerEvents = None.none
                when (marker) {
                    DropMarker.Above -> top = (-0.5).em
                    DropMarker.Below -> bottom = (-0.5).em
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
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
