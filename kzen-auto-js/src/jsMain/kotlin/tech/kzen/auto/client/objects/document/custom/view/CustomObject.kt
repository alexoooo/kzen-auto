package tech.kzen.auto.client.objects.document.custom.view

import emotion.react.css
import mui.material.CardContent
import mui.material.Paper
import mui.system.sx
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.events.DragEvent
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.custom.CustomConventions
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.ObjectMetadata
import web.html.HTMLDivElement
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface CustomObjectProps: Props {
    var objectLocation: ObjectLocation
    var info: CustomObjectInfo
    var customCommander: CustomCommander
    var attributeEditorManager: AttributeEditorManager.Wrapper

    var indexInDocument: Int
    var dropMarker: DropMarker?
    var dragHandlers: CustomDragHandlers
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
    private fun onMouseEnter() {
        if (!state.isHovering) {
            setState { isHovering = true }
        }
    }


    private fun onMouseLeave() {
        if (state.isHovering) {
            setState { isHovering = false }
        }
    }


    private fun onDragOver(event: DragEvent<HTMLDivElement>) {
        event.preventDefault()
        val rect = event.currentTarget.getBoundingClientRect()
        val dropAfter = event.clientY > rect.top + rect.height / 2
        props.dragHandlers.onDragOver(props.indexInDocument, dropAfter)
    }


    private fun onDrop(event: DragEvent<HTMLDivElement>) {
        event.preventDefault()
        props.dragHandlers.onDrop()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            css {
                position = Position.relative
                marginBottom = 1.em
            }

            onMouseEnter = { onMouseEnter() }
            onMouseLeave = { onMouseLeave() }
            onDragOver = ::onDragOver
            onDrop = ::onDrop

            customDragHandle(
                isVisible = state.isHovering || props.dropMarker != null,
                indexInDocument = props.indexInDocument,
                handlers = props.dragHandlers)
            customDropIndicator(props.dropMarker)

            Paper {
                sx {
                    if (props.info.isAbstract) {
                        backgroundColor = Color("rgb(244, 244, 246)")
                        borderStyle = LineStyle.dashed
                        borderWidth = 1.px
                        borderColor = Color("rgb(175, 175, 180)")
                        color = Color("rgb(110, 110, 115)")
                    }
                    else {
                        backgroundColor = NamedColor.white
                    }

                    if (props.info.isExported) {
                        filter = dropShadow(0.px, 0.px, 4.px, Color("rgba(255, 193, 7, 0.55)"))
                    }
                }

                CardContent {
                    CustomObjectHeader::class.react {
                        objectLocation = props.objectLocation
                        info = props.info
                        customCommander = props.customCommander
                    }

                    val objectMetadata = props.info.objectMetadata
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
